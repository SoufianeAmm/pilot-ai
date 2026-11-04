package com.flowpilot.identity.internal.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.flowpilot.identity.internal.application.IdentityProperties;
import com.flowpilot.identity.internal.application.JwtService;

/**
 * Stateless API security. Every request is anonymous unless it carries a valid access token;
 * everything except the endpoints below requires authentication. 401/403 are rendered as
 * problem responses by the shared GlobalExceptionHandler.
 *
 * <p>Spring's CSRF token mechanism is disabled because the API authenticates with bearer tokens,
 * which browsers never attach automatically. The two cookie-authenticated endpoints are protected
 * by {@link CookieRequestGuard} and SameSite=Strict instead.
 *
 * <p>Only loaded with a web server: the one-shot "migrate" mode runs without one.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, IdentityProperties properties,
                                            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/meta").permitAll()
                        // actuator lives on the separate management port, which Nginx never exposes
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        // only served in the dev profile (springdoc is disabled elsewhere)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                resolver.resolveException(request, response, null, ex))
                        .accessDeniedHandler((request, response, ex) ->
                                resolver.resolveException(request, response, null, ex)))
                .addFilterBefore(new CookieRequestGuard(properties.allowedOrigins(), resolver),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwt), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Only needed when the SPA is served from another origin; through Nginx or the Vite proxy it is same-origin. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(IdentityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", CookieRequestGuard.REQUIRED_HEADER,
                "X-Request-Id"));
        cors.setExposedHeaders(List.of("X-Request-Id", "Retry-After", "Location"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
