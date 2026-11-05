package com.flowpilot.organizations.internal.application;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param invitationTtl how long an invitation link stays valid
 * @param acceptUrl     frontend page that receives {@code ?token=...} and calls POST /api/v1/invitations/accept
 */
@Validated
@ConfigurationProperties("flowpilot.organizations")
public record OrganizationProperties(@NotNull Duration invitationTtl, @NotBlank String acceptUrl) {
}
