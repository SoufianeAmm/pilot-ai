package com.flowpilot.identity.internal.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flowpilot.identity.internal.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** @param normalizedEmail result of {@link User#normalizeEmail(String)} */
    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    List<User> findAllByIdIn(Collection<UUID> ids);
}
