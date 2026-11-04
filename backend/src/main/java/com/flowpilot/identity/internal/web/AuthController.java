package com.flowpilot.identity.internal.web;

import java.net.URI;
import java.time.Clock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flowpilot.identity.internal.application.AuthExceptions.InvalidRefreshTokenException;
import com.flowpilot.identity.internal.application.AuthExceptions.RefreshTokenReusedException;
import com.flowpilot.identity.internal.application.AuthService;
import com.flowpilot.identity.internal.application.AuthTokens;
import com.flowpilot.identity.internal.application.ClientInfo;
import com.flowpilot.identity.internal.application.RegisterCommand;
import com.flowpilot.identity.internal.web.AuthDtos.LoginRequest;
import com.flowpilot.identity.internal.web.AuthDtos.RegisterRequest;
import com.flowpilot.identity.internal.web.AuthDtos.TokenResponse;
import com.flowpilot.identity.internal.web.AuthDtos.UserResponse;
import com.flowpilot.shared.web.ApiException;
import com.flowpilot.shared.web.Problems;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService authService;
    private final RefreshCookies cookies;
    private final Clock clock;

    AuthController(AuthService authService, RefreshCookies cookies, Clock clock) {
        this.authService = authService;
        this.cookies = cookies;
        this.clock = clock;
    }

    @PostMapping("/register")
    ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        var user = authService.register(new RegisterCommand(request.email(), request.password(), request.displayName()));
        return ResponseEntity.created(URI.create("/api/v1/me")).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthTokens tokens = authService.login(request.email(), request.password(), ClientInfo.from(http));
        return withRefreshCookie(tokens);
    }

    /** Protected against CSRF by SameSite=Strict, the Origin check and the X-Requested-With header (see SecurityConfig). */
    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
                                          HttpServletRequest http) {
        return withRefreshCookie(authService.refresh(refreshToken, ClientInfo.from(http)));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    /** A dead refresh token is also removed from the browser, so the client goes back to the login page. */
    @ExceptionHandler({InvalidRefreshTokenException.class, RefreshTokenReusedException.class})
    ResponseEntity<ProblemDetail> handleDeadRefreshToken(ApiException ex) {
        ResponseEntity<ProblemDetail> problem = Problems.response(ex);
        return ResponseEntity.status(problem.getStatusCode())
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(problem.getBody());
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(AuthTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(tokens.refreshToken()).toString())
                .body(TokenResponse.from(tokens.accessToken(), clock));
    }
}
