package com.flowpilot.organizations.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.flowpilot.organizations.OrgRole;
import com.flowpilot.organizations.internal.domain.Membership;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByOrgIdAndUserId(UUID orgId, UUID userId);

    boolean existsByOrgIdAndUserId(UUID orgId, UUID userId);

    List<Membership> findAllByUserId(UUID userId);

    List<Membership> findAllByOrgId(UUID orgId);

    Page<Membership> findAllByOrgId(UUID orgId, Pageable pageable);

    long countByOrgIdAndRole(UUID orgId, OrgRole role);
}
