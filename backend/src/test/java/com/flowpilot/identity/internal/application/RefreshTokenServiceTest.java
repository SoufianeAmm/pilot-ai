package com.flowpilot.identity.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditOutcome;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.identity.internal.application.AuthExceptions.InvalidRefreshTokenException;
import com.flowpilot.identity.internal.application.AuthExceptions.RefreshTokenReusedException;
import com.flowpilot.identity.internal.domain.RefreshToken;
import com.flowpilot.identity.internal.persistence.RefreshTokenRepository;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("127.0.0.1", "JUnit");
    private static final String RAW = "raw-refresh-token";

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final AuditTrail audit = mock(AuditTrail.class);
    private final RefreshTokenService service = new RefreshTokenService(repository, audit,
            Clock.fixed(NOW, ZoneOffset.UTC), TestIdentityProperties.defaults());

    private final UUID userId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();

    private RefreshToken storedToken(Instant expiresAt) {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), userId, RefreshTokenService.hash(RAW), familyId,
                expiresAt, NOW.minus(Duration.ofHours(1)), "JUnit");
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void rotate_activeToken_replacesItWithANewTokenInTheSameFamily() {
        RefreshToken current = storedToken(NOW.plus(Duration.ofDays(1)));

        Rotated rotated = service.rotate(RAW, CLIENT);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(familyId);
        assertThat(saved.getValue().isActive(NOW)).isTrue();
        assertThat(current.isReplaced()).isTrue();
        assertThat(current.isActive(NOW)).isFalse();
        assertThat(rotated.userId()).isEqualTo(userId);
        assertThat(rotated.newToken().rawValue()).isNotEqualTo(RAW).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    void rotate_tokenReusedAfterGracePeriod_revokesWholeFamilyAndAudits() {
        RefreshToken current = storedToken(NOW.plus(Duration.ofDays(1)));
        current.replaceWith(UUID.randomUUID(), NOW.minus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> service.rotate(RAW, CLIENT)).isInstanceOf(RefreshTokenReusedException.class);

        verify(repository).revokeFamily(familyId, NOW);
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        assertThat(event.getValue().outcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(event.getValue().actorId()).isEqualTo(userId);
    }

    @Test
    void rotate_tokenReusedWithinGracePeriod_isRejectedWithoutRevokingTheFamily() {
        RefreshToken current = storedToken(NOW.plus(Duration.ofDays(1)));
        current.replaceWith(UUID.randomUUID(), NOW.minus(Duration.ofSeconds(2)));

        assertThatThrownBy(() -> service.rotate(RAW, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void rotate_expiredToken_isRejected() {
        storedToken(NOW.minus(Duration.ofSeconds(1)));

        assertThatThrownBy(() -> service.rotate(RAW, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_loggedOutToken_isRejectedWithoutRevokingTheFamily() {
        RefreshToken current = storedToken(NOW.plus(Duration.ofDays(1)));
        current.revoke(NOW.minus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> service.rotate(RAW, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void rotate_unknownToken_isRejected() {
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("unknown", CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);
    }
}
