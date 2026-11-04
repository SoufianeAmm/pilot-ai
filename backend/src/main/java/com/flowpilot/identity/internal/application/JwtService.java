package com.flowpilot.identity.internal.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.flowpilot.identity.internal.application.AuthExceptions.InvalidAccessTokenException;
import com.flowpilot.identity.internal.domain.User;
import com.flowpilot.shared.id.Ids;
import com.flowpilot.shared.security.AuthenticatedUser;

/**
 * Issues and validates short-lived HS256 access tokens. Claims: iss, sub (user id), email,
 * iat, exp, jti. Roles are deliberately not included (they are checked per request).
 */
@Service
public class JwtService {

    private static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;
    private static final int MIN_KEY_BYTES = 32;
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final Clock clock;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(IdentityProperties properties, Clock clock) {
        this.clock = clock;
        this.issuer = properties.jwt().issuer();
        this.accessTokenTtl = properties.jwt().accessTokenTtl();

        byte[] keyBytes = Base64.getDecoder().decode(properties.jwt().secret());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("flowpilot.security.jwt.secret must be the base64 encoding of at least "
                    + MIN_KEY_BYTES + " random bytes");
        }
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(ALGORITHM).build();
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(ALGORITHM).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(issuer)));
    }

    public AccessToken issueAccessToken(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(Ids.newId().toString())
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(ALGORITHM).build(), claims))
                .getTokenValue();
        return new AccessToken(value, expiresAt);
    }

    /** @throws InvalidAccessTokenException if the signature, issuer or expiry is invalid */
    public AuthenticatedUser parseAndValidate(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            return new AuthenticatedUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            throw new InvalidAccessTokenException(ex);
        }
    }
}
