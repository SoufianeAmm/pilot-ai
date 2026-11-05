package com.flowpilot.organizations.internal.application;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.organizations.OrgRole;
import com.flowpilot.organizations.OrgScope;
import com.flowpilot.organizations.OrganizationEvents.OrganizationCreatedEvent;
import com.flowpilot.organizations.internal.domain.Membership;
import com.flowpilot.organizations.internal.domain.Organization;
import com.flowpilot.organizations.internal.persistence.MembershipRepository;
import com.flowpilot.organizations.internal.persistence.OrganizationRepository;
import com.flowpilot.shared.id.Ids;
import com.flowpilot.shared.web.ApiException;
import com.flowpilot.shared.web.ProblemTypes;

@Service
public class OrganizationService {

    static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int MAX_SLUG_LENGTH = 60;
    private static final String SUFFIX_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OrganizationService(OrganizationRepository organizations, MembershipRepository memberships,
                               AuditTrail audit, ApplicationEventPublisher events, Clock clock) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Creates an organization with the caller as its first OWNER. Without an explicit slug one is
     * derived from the name (with a random suffix if that one is taken).
     */
    @Transactional
    public OrganizationWithRole create(String name, String requestedSlug, UUID ownerId) {
        String cleanName = name.strip();
        String slug = requestedSlug == null || requestedSlug.isBlank()
                ? availableSlugFor(cleanName)
                : requireAvailable(requestedSlug);
        Organization organization = new Organization(Ids.newId(), cleanName, slug, ZoneId.of("UTC"), clock.instant());
        try {
            organizations.saveAndFlush(organization);
        } catch (DataIntegrityViolationException ex) {
            throw slugTaken();
        }
        memberships.save(new Membership(Ids.newId(), organization.getId(), ownerId, OrgRole.OWNER, clock.instant()));
        audit.record(AuditEvent.success(AuditActions.ORG_CREATED, organization.getId(), ownerId)
                .withTarget("organization", organization.getId()));
        events.publishEvent(new OrganizationCreatedEvent(organization.getId(), ownerId));
        return new OrganizationWithRole(organization, OrgRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<OrganizationWithRole> listForUser(UUID userId) {
        Map<UUID, OrgRole> roles = memberships.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(Membership::getOrgId, Membership::getRole));
        if (roles.isEmpty()) {
            return List.of();
        }
        return organizations.findAllByIdIn(roles.keySet()).stream()
                .map(org -> new OrganizationWithRole(org, roles.get(org.getId())))
                .sorted(Comparator.comparing(view -> view.organization().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationWithRole get(OrgScope scope) {
        return new OrganizationWithRole(load(scope), scope.role());
    }

    /** Caller must hold ORG_UPDATE. Null fields are left unchanged. */
    @Transactional
    public OrganizationWithRole update(OrgScope scope, String name, String timeZone, long expectedVersion) {
        Organization organization = load(scope);
        if (!Objects.equals(organization.getVersion(), expectedVersion)) {
            throw ApiException.conflict("The organization was changed by someone else. Reload and try again.");
        }
        if (name != null) {
            organization.rename(name.strip());
        }
        if (timeZone != null) {
            organization.changeTimeZone(parseZone(timeZone));
        }
        organizations.flush();
        audit.record(AuditEvent.success(AuditActions.ORG_UPDATED, scope.orgId(), scope.userId())
                .withTarget("organization", scope.orgId()));
        return new OrganizationWithRole(organization, scope.role());
    }

    private Organization load(OrgScope scope) {
        return organizations.findById(scope.orgId())
                .orElseThrow(() -> ApiException.notFound("Organization not found."));
    }

    private String requireAvailable(String slug) {
        if (!SLUG.matcher(slug).matches() || slug.length() > MAX_SLUG_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION, "Validation failed",
                    "The URL name may only contain lower-case letters, digits and single hyphens.");
        }
        if (organizations.existsBySlug(slug)) {
            throw slugTaken();
        }
        return slug;
    }

    private String availableSlugFor(String name) {
        String base = slugify(name);
        if (!organizations.existsBySlug(base)) {
            return base;
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = base + "-" + randomSuffix();
            if (!organizations.existsBySlug(candidate)) {
                return candidate;
            }
        }
        throw slugTaken();
    }

    /** "Équipe Données & IA" -> "equipe-donnees-ia" */
    static String slugify(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (slug.length() > MAX_SLUG_LENGTH - 5) {
            slug = slug.substring(0, MAX_SLUG_LENGTH - 5).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "org" : slug;
    }

    private static String randomSuffix() {
        StringBuilder suffix = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            suffix.append(SUFFIX_ALPHABET.charAt(RANDOM.nextInt(SUFFIX_ALPHABET.length())));
        }
        return suffix.toString();
    }

    private static ZoneId parseZone(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION, "Validation failed",
                    "Unknown time zone. Use an IANA name such as Africa/Casablanca or Europe/Paris.");
        }
    }

    private static ApiException slugTaken() {
        return ApiException.conflict("This organization URL name is already taken.");
    }
}
