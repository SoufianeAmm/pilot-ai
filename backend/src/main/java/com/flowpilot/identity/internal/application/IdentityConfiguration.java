package com.flowpilot.identity.internal.application;

import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfiguration {

    static final String PASSWORD_ENCODER_ID = "argon2@SpringSecurity_v5_8";

    /** Argon2id for new hashes; the {id} prefix in stored hashes lets the algorithm be upgraded later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = Map.of(
                PASSWORD_ENCODER_ID, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                "bcrypt", new BCryptPasswordEncoder());
        return new DelegatingPasswordEncoder(PASSWORD_ENCODER_ID, encoders);
    }
}
