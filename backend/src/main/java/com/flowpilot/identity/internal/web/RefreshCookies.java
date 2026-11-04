package com.flowpilot.identity.internal.web;

import java.time.Clock;
import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.flowpilot.identity.internal.application.IdentityProperties;
import com.flowpilot.identity.internal.application.IssuedRefreshToken;

/**
 * The refresh token cookie: HttpOnly (JavaScript cannot read it), SameSite=Strict (not sent on
 * cross-site requests) and scoped to the auth endpoints only, so it is never sent to the rest of the API.
 */
@Component
public class RefreshCookies {

    public static final String NAME = "fp_refresh";
    static final String PATH = "/api/v1/auth";

    private final Clock clock;
    private final boolean secure;

    public RefreshCookies(Clock clock, IdentityProperties properties) {
        this.clock = clock;
        this.secure = properties.refreshToken().cookieSecure();
    }

    public ResponseCookie create(IssuedRefreshToken token) {
        return base(token.rawValue()).maxAge(Duration.between(clock.instant(), token.expiresAt())).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite("Strict").path(PATH);
    }
}
