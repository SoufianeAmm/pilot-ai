package com.flowpilot.identity.internal.application;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.identity.IdentityApi;
import com.flowpilot.identity.UserSummary;
import com.flowpilot.identity.internal.domain.User;
import com.flowpilot.identity.internal.persistence.UserRepository;

@Service
@Transactional(readOnly = true)
class IdentityApiService implements IdentityApi {

    private final UserRepository users;

    IdentityApiService(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UserSummary> findUser(UUID userId) {
        return users.findById(userId).map(IdentityApiService::toSummary);
    }

    @Override
    public Map<UUID, UserSummary> findUsers(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllByIdIn(userIds).stream()
                .map(IdentityApiService::toSummary)
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    @Override
    public Optional<UserSummary> findByEmail(String email) {
        return users.findByEmail(User.normalizeEmail(email)).map(IdentityApiService::toSummary);
    }

    private static UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
