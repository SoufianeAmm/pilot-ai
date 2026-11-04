package com.flowpilot.identity.internal.application;

import java.time.Clock;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.identity.UserRegisteredEvent;
import com.flowpilot.identity.UserSummary;
import com.flowpilot.identity.internal.application.AuthExceptions.EmailAlreadyRegisteredException;
import com.flowpilot.identity.internal.application.AuthExceptions.InvalidCredentialsException;
import com.flowpilot.identity.internal.application.AuthExceptions.InvalidRefreshTokenException;
import com.flowpilot.identity.internal.application.AuthExceptions.TooManyLoginAttemptsException;
import com.flowpilot.identity.internal.domain.User;
import com.flowpilot.identity.internal.persistence.UserRepository;
import com.flowpilot.shared.id.Ids;

/**
 * Registration, login, refresh and logout. Not transactional as a whole: password hashing is
 * slow on purpose, so no database connection is held while it runs; each repository call or
 * token operation runs in its own short transaction.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptLimiter limiter;
    private final PasswordPolicy passwordPolicy;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    /** Checked when the email is unknown, so the response time does not reveal registered emails. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwt,
                       RefreshTokenService refreshTokens, LoginAttemptLimiter limiter, PasswordPolicy passwordPolicy,
                       AuditTrail audit, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.limiter = limiter;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalisation-placeholder-password");
    }

    public UserSummary register(RegisterCommand command) {
        String email = User.normalizeEmail(command.email());
        passwordPolicy.validate(command.password());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = new User(Ids.newId(), email, passwordEncoder.encode(command.password()),
                command.displayName().strip(), clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // two registrations with the same email at the same moment: the unique constraint decides
            throw new EmailAlreadyRegisteredException();
        }
        audit.record(AuditEvent.success(AuditActions.AUTH_REGISTERED, null, user.getId()));
        events.publishEvent(new UserRegisteredEvent(user.getId(), email, user.getCreatedAt()));
        return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName());
    }

    public AuthTokens login(String rawEmail, String password, ClientInfo client) {
        String email = User.normalizeEmail(rawEmail);
        try {
            limiter.checkAllowed(email, client.ip());
        } catch (TooManyLoginAttemptsException ex) {
            audit.record(AuditEvent.denied(AuditActions.AUTH_LOGIN_RATE_LIMITED, null, null).withDetail("email", email));
            throw ex;
        }
        Optional<User> user = users.findByEmail(email);
        boolean passwordMatches = passwordEncoder.matches(password, user.map(User::getPasswordHash).orElse(dummyHash));
        if (!passwordMatches || user.isEmpty() || !user.get().isActive()) {
            limiter.recordFailure(email, client.ip());
            audit.record(AuditEvent.failure(AuditActions.AUTH_LOGIN_FAILED, null, user.map(User::getId).orElse(null))
                    .withDetail("email", email));
            throw new InvalidCredentialsException();
        }
        limiter.reset(email, client.ip());
        User authenticated = user.get();
        AuthTokens tokens = new AuthTokens(jwt.issueAccessToken(authenticated),
                refreshTokens.issue(authenticated, client));
        audit.record(AuditEvent.success(AuditActions.AUTH_LOGIN_SUCCEEDED, null, authenticated.getId()));
        return tokens;
    }

    public AuthTokens refresh(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        Rotated rotated = refreshTokens.rotate(rawRefreshToken, client);
        User user = users.findById(rotated.userId())
                .filter(User::isActive)
                .orElseThrow(InvalidRefreshTokenException::new);
        return new AuthTokens(jwt.issueAccessToken(user), rotated.newToken());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revoke(rawRefreshToken);
        }
    }
}
