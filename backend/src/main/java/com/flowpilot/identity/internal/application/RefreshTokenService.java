package com.flowpilot.identity.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.identity.internal.application.AuthExceptions.InvalidRefreshTokenException;
import com.flowpilot.identity.internal.application.AuthExceptions.RefreshTokenReusedException;
import com.flowpilot.identity.internal.domain.RefreshToken;
import com.flowpilot.identity.internal.domain.User;
import com.flowpilot.identity.internal.persistence.RefreshTokenRepository;
import com.flowpilot.shared.id.Ids;

/**
 * Refresh tokens: 256-bit random values, stored only as SHA-256 hashes, rotated on every use.
 * Presenting a token that was already rotated means it was copied: the whole family is revoked.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final AuditTrail audit;
    private final Clock clock;
    private final Duration ttl;
    private final Duration reuseGrace;

    public RefreshTokenService(RefreshTokenRepository repository, AuditTrail audit, Clock clock,
                               IdentityProperties properties) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
        this.ttl = properties.refreshToken().ttl();
        this.reuseGrace = properties.refreshToken().reuseGrace();
    }

    /** Starts a new token family (one per login). */
    @Transactional
    public IssuedRefreshToken issue(User user, ClientInfo client) {
        return create(Ids.newId(), user.getId(), Ids.newId(), client);
    }

    /**
     * Exchanges a valid refresh token for a new one in the same family.
     * The family revocation must be committed even though an exception is thrown, hence noRollbackFor.
     */
    @Transactional(noRollbackFor = RefreshTokenReusedException.class)
    public Rotated rotate(String rawToken, ClientInfo client) {
        RefreshToken current = repository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (current.isReplaced()) {
            if (current.getRevokedAt().plus(reuseGrace).isAfter(now)) {
                // most likely two browser tabs refreshing at the same moment, not theft
                throw new InvalidRefreshTokenException();
            }
            repository.revokeFamily(current.getFamilyId(), now);
            audit.record(AuditEvent.denied(AuditActions.AUTH_REFRESH_REUSE_DETECTED, null, current.getUserId())
                    .withDetail("familyId", current.getFamilyId().toString()));
            throw new RefreshTokenReusedException(current.getUserId());
        }
        if (!current.isActive(now)) {
            throw new InvalidRefreshTokenException();
        }
        UUID nextId = Ids.newId();
        IssuedRefreshToken next = create(nextId, current.getUserId(), current.getFamilyId(), client);
        current.replaceWith(nextId, now);
        return new Rotated(current.getUserId(), next);
    }

    /** Logout: revokes this token only. Unknown or already revoked tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional
    public void revokeFamily(UUID familyId) {
        repository.revokeFamily(familyId, clock.instant());
    }

    private IssuedRefreshToken create(UUID id, UUID userId, UUID familyId, ClientInfo client) {
        byte[] random = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(random);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        repository.save(new RefreshToken(id, userId, hash(rawValue), familyId, expiresAt, now, client.userAgent()));
        return new IssuedRefreshToken(rawValue, expiresAt);
    }

    static byte[] hash(String rawToken) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
