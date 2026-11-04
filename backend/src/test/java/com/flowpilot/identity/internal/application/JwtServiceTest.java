package com.flowpilot.identity.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.flowpilot.identity.internal.application.AuthExceptions.InvalidAccessTokenException;
import com.flowpilot.identity.internal.domain.User;
import com.flowpilot.shared.security.AuthenticatedUser;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    private final User user = new User(UUID.randomUUID(), "mia@example.com", "{noop}x", "Mia", NOW);

    private static JwtService serviceAt(Instant instant) {
        return new JwtService(TestIdentityProperties.defaults(), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void issueAndParse_roundTrip_returnsUserIdAndEmail() {
        JwtService jwt = serviceAt(NOW);

        AccessToken token = jwt.issueAccessToken(user);
        AuthenticatedUser parsed = jwt.parseAndValidate(token.value());

        assertThat(parsed).isEqualTo(new AuthenticatedUser(user.getId(), "mia@example.com"));
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void parse_afterExpiryPlusSkew_isRejected() {
        AccessToken token = serviceAt(NOW).issueAccessToken(user);

        JwtService later = serviceAt(NOW.plus(Duration.ofMinutes(16)));

        assertThatThrownBy(() -> later.parseAndValidate(token.value()))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void parse_tamperedPayload_isRejected() {
        String token = serviceAt(NOW).issueAccessToken(user).value();
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + UUID.randomUUID() + "\",\"iss\":\"flowpilot\"}").getBytes());

        assertThatThrownBy(() -> serviceAt(NOW).parseAndValidate(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void parse_tokenSignedWithAnotherKey_isRejected() {
        String otherSecret = Base64.getEncoder().encodeToString("another-secret-key-that-is-long-enough!!".getBytes());
        JwtService other = new JwtService(TestIdentityProperties.withSecret(otherSecret), Clock.fixed(NOW, ZoneOffset.UTC));
        String foreignToken = other.issueAccessToken(user).value();

        assertThatThrownBy(() -> serviceAt(NOW).parseAndValidate(foreignToken))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void parse_garbage_isRejected() {
        assertThatThrownBy(() -> serviceAt(NOW).parseAndValidate("not-a-jwt"))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void constructor_secretShorterThan32Bytes_failsFast() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());

        assertThatThrownBy(() -> new JwtService(TestIdentityProperties.withSecret(shortSecret), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }
}
