"""Builds docs/uml/FlowPilot-UML-Identity-Organizations-AI.pdf.

Run:  python build_pdf.py   (needs: pip install reportlab; Windows fonts Arial + Consolas)
"""
import datetime
import os

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import (BaseDocTemplate, Frame, KeepTogether, PageBreak, PageTemplate, Paragraph,
                                Spacer, Table, TableStyle)

import uml
from uml import BOLD, FONT, ITALIC, MONO, ClassDiagram, SequenceDiagram, UseCaseDiagram

uml.register_fonts()

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "FlowPilot-UML-Identity-Organizations-AI.pdf"))
VERSION, DATE = "1.0", datetime.date(2026, 11, 4).isoformat()
DOC_TITLE = "FlowPilot AI: UML design of Identity, Organizations, Audit and AI integration"

LEGEND = [("mine", "Soufiane: identity, organizations, audit, AI"),
          ("friend", "Other teams: contract to implement"),
          ("shared", "Shared kernel"),
          ("external", "Framework / external system")]

# ----------------------------------------------------------------------------- styles
INK = colors.HexColor("#1F2937")
ACCENT = colors.HexColor("#2F5FC4")
styles = {
    "title": ParagraphStyle("title", fontName=BOLD, fontSize=24, leading=30, textColor=INK, spaceAfter=6),
    "subtitle": ParagraphStyle("subtitle", fontName=FONT, fontSize=12.5, leading=17, textColor=colors.HexColor("#4B5563")),
    "h1": ParagraphStyle("h1", fontName=BOLD, fontSize=15, leading=19, textColor=INK, spaceAfter=3),
    "h2": ParagraphStyle("h2", fontName=BOLD, fontSize=11.5, leading=15, textColor=INK, spaceBefore=8, spaceAfter=3),
    "body": ParagraphStyle("body", fontName=FONT, fontSize=9.3, leading=13, textColor=INK, alignment=TA_LEFT),
    "intro": ParagraphStyle("intro", fontName=FONT, fontSize=9, leading=12.3, textColor=colors.HexColor("#374151"),
                            spaceAfter=6),
    "bullet": ParagraphStyle("bullet", fontName=FONT, fontSize=9.2, leading=12.8, textColor=INK, leftIndent=12,
                             bulletIndent=2, spaceAfter=2.5),
    "cell": ParagraphStyle("cell", fontName=FONT, fontSize=8.2, leading=10.4, textColor=INK),
    "cellcenter": ParagraphStyle("cellcenter", fontName=FONT, fontSize=8.2, leading=10.4, textColor=INK,
                                 alignment=1),
    "cellmono": ParagraphStyle("cellmono", fontName=MONO, fontSize=7.9, leading=10.2, textColor=INK),
    "cellhead": ParagraphStyle("cellhead", fontName=BOLD, fontSize=8.3, leading=10.4, textColor=colors.white),
}


def P(text, style="body"):
    return Paragraph(text, styles[style])


def code(text):
    return f'<font name="{MONO}">{text}</font>'


def table(rows, widths, mono_cols=(), center_cols=(), header_bg=ACCENT):
    data = []
    for r, row in enumerate(rows):
        cells = []
        for c, value in enumerate(row):
            if r == 0:
                cells.append(Paragraph(value, styles["cellhead"]))
            else:
                style = "cellmono" if c in mono_cols else ("cellcenter" if c in center_cols else "cell")
                cells.append(Paragraph(value, styles[style]))
        data.append(cells)
    t = Table(data, colWidths=widths, repeatRows=1)
    cmds = [
        ("BACKGROUND", (0, 0), (-1, 0), header_bg),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#D1D5DB")),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F5F7FB")]),
        ("TOPPADDING", (0, 0), (-1, -1), 3), ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ("LEFTPADDING", (0, 0), (-1, -1), 5), ("RIGHTPADDING", (0, 0), (-1, -1), 5),
    ]
    for c in center_cols:
        cmds.append(("ALIGN", (c, 1), (c, -1), "CENTER"))
    t.setStyle(TableStyle(cmds))
    return t


# ----------------------------------------------------------------------------- helpers for layout
def right(b, gap=40):
    return b.x + b.w + gap


def below(b, gap=30):
    return b.y + b.h + gap


# ============================================================================= DIAGRAMS
def module_overview():
    d = ClassDiagram(LEGEND)
    w1 = 205
    identity = d.box("identity", "identity", 0, 0, kind="package", width=w1,
                     attrs=["IdentityApi", "UserSummary", "UserRegisteredEvent"])
    orgs = d.box("orgs", "organizations", right(identity, 70), 0, kind="package", width=w1,
                 attrs=["OrganizationAccess", "OrganizationDirectory", "OrgScope, OrgRole, OrgPermission",
                        "MemberSummary", "OrganizationCreatedEvent, Member*Event"])
    audit = d.box("audit", "audit", right(orgs, 70), 0, kind="package", width=w1,
                  attrs=["AuditTrail", "AuditEvent, AuditOutcome", "AuditActions (constants)"])
    ai = d.box("ai", "ai", right(audit, 70), 0, kind="package", width=w1,
               attrs=["No Java API for other modules.", "REST + UI only. Consumes the", "query interfaces below."])
    y2 = 235
    w2 = 172
    names = [("projects", "projects", ["ProjectAccess", "ProjectScope, ProjectRole,", "ProjectPermission",
                                       "ProjectQueries"]),
             ("tasks", "tasks", ["TaskQueries"]),
             ("collab", "collaboration", ["ActivityQueries"]),
             ("analytics", "analytics", ["ProjectMetrics"]),
             ("documents", "documents", ["KnowledgeSearch (Phase 4)"]),
             ("notif", "notifications", ["listens to Member*Event"])]
    x = 0
    for id_, name, attrs in names:
        b = d.box(id_, name, x, y2, kind="package", width=w2, attrs=attrs, tone="friend")
        x = right(b, 26)
    shared = d.box("shared", "shared (used by every module)", 0, 385, kind="package", width=x - 26, tone="shared",
                   attrs=["security.AuthenticatedUser   web.ApiException, ProblemTypes, RequestIdFilter   "
                          "time.Clock (inject; never call Instant.now())"])
    d.rel("orgs", "identity", "dep", "IdentityApi", src="l", dst="r")
    d.rel("ai", "audit", "dep", "AuditTrail", src="l", dst="r")
    d.rel("projects", "orgs", "dep", "OrganizationAccess", src=("t", 0.5), dst=("b", 0.25),
          via=[(86, 190), (orgs.x + orgs.w * 0.25, 190)])
    d.rel("notif", "orgs", "dep", "Member*Event (events)", src=("t", 0.5), dst=("b", 0.75),
          via=[(d.boxes["notif"].x + 86, 205), (orgs.x + orgs.w * 0.75, 205)], label_pos=0.15)
    for k, target in enumerate(["projects", "tasks", "collab", "analytics", "documents"]):
        tb = d.boxes[target]
        d.rel("ai", target, "dep", "read-only queries" if target == "analytics" else None,
              src=("b", 0.15 + 0.17 * k), dst=("t", 0.75))
    d.rel("orgs", "audit", "dep", "record(...)", src="r", dst="l")
    return d


def uc_identity():
    d = UseCaseDiagram(LEGEND)
    d.boundary(115, 0, 600, 520, "FlowPilot AI: identity and organizations")
    c1, c2, c3 = 265, 430, 590
    d.uc("register", "Register", c1, 55)
    d.uc("login", "Log in", c1, 110)
    d.uc("refresh", "Refresh session\n(automatic)", c1, 170)
    d.uc("logout", "Log out", c1, 230)
    d.uc("profile", "View my profile", c1, 285)
    d.uc("create", "Create organization", c1, 340)
    d.uc("accept", "Accept invitation", c1, 395)
    d.uc("switch", "Switch organization", c1, 450)
    d.uc("members", "View members", c1, 500)
    d.uc("ratelimit", "Check login\nrate limit", c2, 85)
    d.uc("auditrec", "Record audit entry", c2, 300, rx=62)
    d.uc("email", "Send invitation\nemail", c2, 175)
    d.uc("invite", "Invite member", c3, 110)
    d.uc("revoke", "Revoke invitation", c3, 165)
    d.uc("role", "Change member role", c3, 225)
    d.uc("remove", "Remove member", c3, 285)
    d.uc("audit", "View audit log", c3, 345)
    d.uc("update", "Update organization\nsettings", c3, 430)
    d.actor("visitor", "Visitor", 40, 55)
    d.actor("user", "Authenticated user", 40, 245)
    d.actor("member", "Org member", 40, 430)
    d.actor("admin", "Org admin", 790, 180)
    d.actor("owner", "Org owner", 790, 380)
    for u in ("register", "login"):
        d.link("visitor", u)
    for u in ("refresh", "logout", "profile", "create", "accept", "switch"):
        d.link("user", u)
    d.link("member", "members")
    d.generalize("member", "user")
    for u in ("invite", "revoke", "role", "remove", "audit"):
        d.link("admin", u)
    d.link("owner", "update")
    d.generalize("owner", "admin")
    d.include("login", "ratelimit")
    d.include("invite", "email")
    for u in ("login", "role", "remove", "accept"):
        d.include(u, "auditrec")
    d.note(740, 470, "Org admin and owner can also do everything an org member can. "
                     "Admins cannot change or remove owners; the last owner cannot leave.", width=175)
    return d


def uc_ai():
    d = UseCaseDiagram(LEGEND)
    d.boundary(115, 0, 665, 500, "FlowPilot AI: AI Project Analyst")
    c1, c2 = 260, 470
    d.uc("start", "Start conversation", c1, 60)
    d.uc("ask", "Ask a project question", c1, 150, rx=74)
    d.uc("view", "View answer with\nevidence and risks", c1, 235)
    d.uc("open", "Open referenced\ntask / milestone", c1, 315)
    d.uc("history", "View my conversation\nhistory", c1, 390)
    d.uc("weekly", "Generate weekly\nsummary", c1, 460)
    d.uc("quota", "Check AI quota", c2, 55)
    d.uc("authz", "Authorize project\naccess (AI_ASK)", c2, 120)
    d.uc("tools", "Retrieve project data\nvia read-only tools", c2, 200)
    d.uc("generate", "Generate structured\nanswer", c2, 280)
    d.uc("validate", "Validate answer\nand evidence refs", c2, 355)
    d.uc("docs", "Search project documents\n(Phase 4)", 672, 135, tone="friend")
    d.actor("member", "Project member", 40, 200)
    d.actor("llm", "LLM (Ollama)", 860, 215, tone="external")
    for u in ("start", "ask", "view", "open", "history", "weekly"):
        d.link("member", u)
    for u in ("quota", "authz", "tools", "generate", "validate"):
        d.include("ask", u)
    d.include("weekly", "generate")
    d.extend("docs", "tools")
    d.link("llm", "tools")
    d.link("llm", "generate")
    d.note(800, 320, "Project member = any project role (VIEWER, CONTRIBUTOR, MANAGER) with the AI_ASK "
                     "permission. The analyst is read-only: it never creates, edits or deletes project data.",
           width=190)
    return d


def cd_identity_auth():
    d = ClassDiagram(LEGEND)
    ctrl = d.box("ctrl", "AuthController", 0, 0, stereotype="controller  /api/v1/auth",
                 attrs=["- authService: AuthService", "- cookies: RefreshCookies"],
                 methods=["+ register(RegisterRequest): ResponseEntity<UserResponse>",
                          "+ login(LoginRequest, HttpServletRequest): ResponseEntity<TokenResponse>",
                          "+ refresh(String refreshCookie, HttpServletRequest): ResponseEntity<TokenResponse>",
                          "+ logout(String refreshCookie): ResponseEntity<Void>"])
    dtos = d.box("dtos", "Web DTOs", 0, below(ctrl, 40), kind="record", stereotype="records (request / response)",
                 attrs=["RegisterRequest(email, password, displayName)", "LoginRequest(email, password)",
                        "TokenResponse(accessToken, expiresIn, tokenType)", "UserResponse(id, email, displayName)"])
    cookies = d.box("cookies", "RefreshCookies", 0, below(dtos, 40),
                    attrs=["name = \"fp_refresh\", path = \"/api/v1/auth\"", "HttpOnly, Secure, SameSite=Strict"],
                    methods=["+ create(IssuedRefreshToken): ResponseCookie", "+ clear(): ResponseCookie"])
    svc = d.box("svc", "AuthService", right(ctrl, 70), 0, stereotype="service",
                attrs=["- users: UserRepository", "- passwordEncoder: PasswordEncoder", "- jwt: JwtService",
                       "- refreshTokens: RefreshTokenService", "- limiter: LoginAttemptLimiter",
                       "- passwordPolicy: PasswordPolicy", "- audit: AuditTrail"],
                methods=["+ register(RegisterCommand): UserSummary",
                         "+ login(String email, String password, ClientInfo): AuthTokens",
                         "+ refresh(String rawToken, ClientInfo): AuthTokens",
                         "+ logout(String rawToken): void"])
    jwt = d.box("jwt", "JwtService", right(svc, 70), 0, stereotype="service",
                attrs=["- signingKey: SecretKey  (from env)", "- accessTtl: Duration = 15 min", "- clock: Clock"],
                methods=["+ issueAccessToken(User): AccessToken", "+ parseAndValidate(String token): AuthenticatedUser"])
    rts = d.box("rts", "RefreshTokenService", jwt.x, below(jwt, 30), stereotype="service",
                attrs=["- repository: RefreshTokenRepository", "- refreshTtl: Duration = 7 days", "- clock: Clock"],
                methods=["+ issue(User, ClientInfo): IssuedRefreshToken",
                         "+ rotate(String rawToken, ClientInfo): Rotated",
                         "+ revoke(String rawToken): void", "+ revokeFamily(UUID familyId): void",
                         "- hash(String rawToken): byte[]  (SHA-256)"])
    lim = d.box("lim", "LoginAttemptLimiter", svc.x, below(svc, 40), stereotype="service",
                attrs=["- buckets: ProxyManager  (Bucket4j, PostgreSQL)"],
                methods=["+ checkAllowed(String email, String ip): void", "+ recordFailure(String email, String ip): void",
                         "+ reset(String email, String ip): void"])
    pol = d.box("pol", "PasswordPolicy", svc.x, below(lim, 30),
                methods=["+ validate(String password): void  (12..128 chars)"])
    enc = d.box("enc", "PasswordEncoder", jwt.x, below(rts, 24), kind="interface", tone="external",
                stereotype="interface  Spring Security", methods=["+ encode(CharSequence): String",
                                                               "+ matches(CharSequence, String): boolean"])
    audit = d.box("audit", "AuditTrail", jwt.x, below(enc, 24), kind="interface", stereotype="interface  audit",
                  methods=["+ record(AuditEvent): void"])
    yrec = max(below(cookies, 40), below(pol, 40), below(audit, 40))
    tokens = d.box("tokens", "AuthTokens", 0, yrec, kind="record",
                   attrs=["accessToken: AccessToken", "refreshToken: IssuedRefreshToken"])
    access = d.box("access", "AccessToken", right(tokens, 30), yrec, kind="record",
                   attrs=["value: String", "expiresAt: Instant"])
    issued = d.box("issued", "IssuedRefreshToken", right(access, 30), yrec, kind="record",
                   attrs=["rawValue: String", "expiresAt: Instant"])
    rotated = d.box("rotated", "Rotated", right(issued, 30), yrec, kind="record",
                    attrs=["user: User", "newToken: IssuedRefreshToken"])
    client = d.box("client", "ClientInfo", right(rotated, 30), yrec, kind="record",
                   attrs=["ip: String", "userAgent: String"])
    cmd = d.box("cmd", "RegisterCommand", right(client, 30), yrec, kind="record",
                attrs=["email, password, displayName"])
    exc = d.box("exc", "Auth exceptions", right(cmd, 30), yrec, stereotype="extends ApiException  (HTTP status)",
                attrs=["EmailAlreadyRegisteredException  409", "WeakPasswordException            400",
                       "InvalidCredentialsException      401", "InvalidRefreshTokenException     401",
                       "RefreshTokenReusedException      401", "TooManyLoginAttemptsException    429"])
    d.rel("ctrl", "svc", "assoc", "uses", src="r", dst=("l", 0.2))
    d.rel("ctrl", "cookies", "assoc", None, src=("l", 0.8), dst=("l", 0.5), via=[(-20, ctrl.y + ctrl.h * 0.8),
                                                                                 (-20, cookies.y + cookies.h * 0.5)])
    d.rel("ctrl", "dtos", "dep", "accepts / returns", src="b", dst="t")
    bus = svc.x + svc.w + 30   # vertical routing lane between AuthService and its collaborators
    for target, frac in (("jwt", 0.1), ("rts", 0.3), ("enc", 0.5), ("audit", 0.7)):
        tb = d.boxes[target]
        d.rel("svc", target, "assoc", None, src=("r", frac), dst=("l", 0.5),
              via=[(bus + 8 * frac, svc.y + svc.h * frac), (bus + 8 * frac, tb.y + tb.h * 0.5)])
    d.rel("svc", "lim", "assoc", None, src=("b", 0.3), dst=("t", 0.3))
    d.rel("svc", "pol", "assoc", None, src=("l", 0.95), dst=("l", 0.5),
          via=[(svc.x - 22, svc.y + svc.h * 0.95), (svc.x - 22, pol.y + pol.h * 0.5)])
    d.rel("svc", "tokens", "dep", "returns", src=("l", 0.85), dst=("t", 0.85),
          via=[(svc.x - 40, svc.y + svc.h * 0.85), (svc.x - 40, yrec - 22), (tokens.x + tokens.w * 0.85, yrec - 22)],
          label_pos=0.5)
    d.rel("tokens", "access", "compose", None, src="r", dst="l")
    d.rel("rts", "issued", "dep", "creates", src=("r", 0.5), dst=("t", 0.5),
          via=[(rts.x + rts.w + 20, rts.y + rts.h * 0.5), (rts.x + rts.w + 20, yrec - 12),
               (issued.x + issued.w * 0.5, yrec - 12)], label_pos=0.6)
    return d


def cd_identity_core():
    d = ClassDiagram(LEGEND)
    cfg = d.box("cfg", "SecurityConfig", 0, 0, stereotype="configuration",
                methods=["+ securityFilterChain(HttpSecurity): SecurityFilterChain",
                         "+ passwordEncoder(): PasswordEncoder  (Argon2id)",
                         "+ corsConfigurationSource(): CorsConfigurationSource"])
    filt = d.box("filter", "JwtAuthenticationFilter", 0, below(cfg, 40), stereotype="extends OncePerRequestFilter",
                 attrs=["- jwt: JwtService"],
                 methods=["# doFilterInternal(request, response, chain): void"])
    jwt = d.box("jwt", "JwtService", 0, below(filt, 40), stereotype="service  (see previous page)")
    principal = d.box("principal", "AuthenticatedUser", right(cfg, 60), 0, kind="record", tone="shared",
                      stereotype="record  shared.security",
                      attrs=["userId: UUID", "email: String"])
    me = d.box("me", "MeController", principal.x, below(principal, 40), stereotype="controller  GET /api/v1/me",
               methods=["+ me(AuthenticatedUser): UserResponse"])
    api = d.box("api", "IdentityApi", right(principal, 140), 0, kind="interface",
                stereotype="interface  public module API",
                methods=["+ findUser(UUID userId): Optional<UserSummary>",
                         "+ findUsers(Set<UUID> userIds): Map<UUID, UserSummary>",
                         "+ findByEmail(String email): Optional<UserSummary>"])
    summary = d.box("summary", "UserSummary", right(api, 40), 0, kind="record",
                    attrs=["id: UUID", "email: String", "displayName: String"])
    event = d.box("event", "UserRegisteredEvent", summary.x, below(summary, 24), kind="record", stereotype="event record",
                  attrs=["userId: UUID", "email: String", "occurredAt: Instant"])
    impl = d.box("impl", "IdentityApiService", api.x, below(api, 50), stereotype="service",
                 attrs=["- users: UserRepository"])
    yd = max(below(jwt, 50), below(impl, 60), below(me, 60))
    user = d.box("user", "User", 0, yd, kind="entity",
                 attrs=["- id: UUID", "- email: String  (citext, unique)", "- passwordHash: String",
                        "- displayName: String", "- status: UserStatus", "- createdAt: Instant", "- version: long"],
                 methods=["+ changeDisplayName(String): void", "+ disable(): void", "+ isActive(): boolean"])
    status = d.box("status", "UserStatus", 0, below(user, 30), kind="enum", attrs=["ACTIVE", "DISABLED"])
    token = d.box("token", "RefreshToken", right(user, 70), yd, kind="entity",
                  attrs=["- id: UUID", "- userId: UUID", "- tokenHash: byte[]  (unique)", "- familyId: UUID",
                         "- expiresAt: Instant", "- revokedAt: Instant", "- replacedById: UUID", "- createdAt: Instant"],
                  methods=["+ isActive(Instant now): boolean", "+ revoke(Instant now): void",
                           "+ replaceWith(UUID newId, Instant now): void"])
    urepo = d.box("urepo", "UserRepository", right(token, 70), yd, kind="interface",
                  stereotype="interface  JpaRepository<User, UUID>",
                  methods=["+ findByEmailIgnoreCase(String): Optional<User>", "+ existsByEmailIgnoreCase(String): boolean",
                           "+ findAllByIdIn(Collection<UUID>): List<User>"])
    trepo = d.box("trepo", "RefreshTokenRepository", urepo.x, below(urepo, 30), kind="interface",
                  stereotype="interface  JpaRepository<RefreshToken, UUID>",
                  methods=["+ findByTokenHash(byte[]): Optional<RefreshToken>",
                           "+ revokeFamily(UUID familyId, Instant now): int", "+ deleteExpired(Instant before): int"])
    d.group(api.x - 14, -24, right(summary, 14) - (api.x - 14), below(event, 14) + 24, "public API  com.flowpilot.identity",
            "mine")
    d.rel("cfg", "filter", "assoc", "registers", src="b", dst="t")
    d.rel("filter", "jwt", "assoc", "validates token", src="b", dst="t")
    d.rel("filter", "principal", "dep", "puts in SecurityContext", src="r", dst=("l", 0.7),
          via=[(filt.x + filt.w + 30, filt.y + filt.h / 2), (filt.x + filt.w + 30, principal.y + principal.h * 0.7)],
          label_pos=0.5)
    d.rel("me", "principal", "dep", "@AuthenticationPrincipal", src="t", dst="b")
    d.rel("impl", "api", "realize", None, src="t", dst="b")
    d.rel("api", "summary", "dep", None, src="r", dst=("l", 0.5))
    d.rel("impl", "urepo", "assoc", None, src="b", dst="t")
    d.rel("user", "status", "assoc", None, src="b", dst="t")
    d.rel("user", "token", "assoc", None, src=("r", 0.3), dst=("l", 0.3), a_label="1", b_label="0..*")
    d.rel("urepo", "user", "dep", "manages", src=("l", 0.85), dst=("r", 0.85),
          via=[(urepo.x - 20, urepo.y + urepo.h * 0.85), (urepo.x - 20, user.y + user.h + 16),
               (user.x + user.w + 20, user.y + user.h + 16), (user.x + user.w + 20, user.y + user.h * 0.85)],
          label_pos=0.5)
    d.rel("trepo", "token", "dep", "manages", src="l", dst=("r", 0.75),
          via=[(token.x + token.w + 25, trepo.y + trepo.h / 2), (token.x + token.w + 25, token.y + token.h * 0.75)])
    d.note(principal.x, below(me, 25), "Every module: inject the caller with "
           "@AuthenticationPrincipal AuthenticatedUser user. Roles are NOT in the JWT; they are checked per request "
           "(OrganizationAccess / ProjectAccess), so revoked access works immediately.", width=230)
    return d


def cd_org_api():
    d = ClassDiagram(LEGEND)
    access = d.box("access", "OrganizationAccess", 0, 0, kind="interface", stereotype="interface  public API",
                   methods=["+ requireMembership(UUID orgId, UUID userId): OrgScope",
                            "+ requirePermission(UUID orgId, UUID userId, OrgPermission): OrgScope",
                            "+ findRole(UUID orgId, UUID userId): Optional<OrgRole>"])
    directory = d.box("directory", "OrganizationDirectory", 0, below(access, 30), kind="interface",
                      stereotype="interface  public API",
                      methods=["+ findMember(UUID orgId, UUID userId): Optional<MemberSummary>",
                               "+ listMembers(UUID orgId): List<MemberSummary>"])
    scope = d.box("scope", "OrgScope", right(access, 70), 0, kind="record",
                  attrs=["orgId: UUID", "userId: UUID", "role: OrgRole"],
                  methods=["+ has(OrgPermission): boolean", "+ require(OrgPermission): void  (403 if missing)"])
    role = d.box("role", "OrgRole", scope.x, below(scope, 40), kind="enum",
                 attrs=["OWNER", "ADMIN", "PROJECT_MANAGER", "MEMBER"],
                 methods=["+ permissions(): Set<OrgPermission>", "+ outranks(OrgRole other): boolean"])
    perm = d.box("perm", "OrgPermission", right(scope, 60), below(scope, 40), kind="enum",
                 attrs=["ORG_UPDATE", "ORG_DELETE", "MEMBER_INVITE", "MEMBER_MANAGE", "PROJECT_CREATE",
                        "PROJECT_MANAGE_ALL", "AUDIT_VIEW"])
    member = d.box("member", "MemberSummary", right(scope, 60), 0, kind="record",
                   attrs=["userId: UUID", "displayName: String", "email: String", "role: OrgRole", "joinedAt: Instant"])
    events = d.box("events", "Organization events", right(member, 50), 0, stereotype="event records  (published)",
                   attrs=["OrganizationCreatedEvent(orgId, ownerId)", "MemberJoinedEvent(orgId, userId, role)",
                          "MemberRoleChangedEvent(orgId, userId,", "    oldRole, newRole, changedBy)",
                          "MemberRemovedEvent(orgId, userId, removedBy)"])
    group_bottom = below(perm, 14)
    d.group(-14, -24, right(events, 14) + 14, group_bottom + 24, "public API  com.flowpilot.organizations", "mine")
    yi = group_bottom + 50
    impl = d.box("impl", "OrganizationAccessService", 0, yi, stereotype="service  (internal)",
                 attrs=["- memberships: MembershipRepository"],
                 methods=["+ requireMembership(...)  -> 404 if not a member",
                          "+ requirePermission(...) -> 404 / 403", "+ findMember(...), listMembers(...)"])
    policy = d.box("policy", "RolePolicy", right(impl, 60), yi, stereotype="service  (internal)",
                   methods=["+ canAssign(OrgRole actor, OrgRole currentTarget, OrgRole newRole): boolean",
                            "+ canRemove(OrgRole actor, OrgRole target): boolean",
                            "+ ensureOwnerRemains(UUID orgId, UUID ownerLosingRole): void  (409)"])
    pa = d.box("pa", "ProjectAccess", right(policy, 60), yi, kind="interface", tone="friend",
               stereotype="interface  projects team",
               methods=["+ require(orgId, projectId, userId,", "      ProjectPermission): ProjectScope"])
    d.rel("impl", "access", "realize", None, src=("l", 0.3), dst=("l", 0.5),
          via=[(-30, impl.y + impl.h * 0.3), (-30, access.y + access.h * 0.5)])
    d.rel("impl", "directory", "realize", None, src=("t", 0.5), dst=("b", 0.5),
          via=[(impl.x + impl.w * 0.5, yi - 25), (directory.x + directory.w * 0.5, yi - 25)])
    d.rel("access", "scope", "dep", "returns", src=("r", 0.4), dst=("l", 0.4))
    d.rel("scope", "role", "assoc", None, src="b", dst="t")
    d.rel("role", "perm", "assoc", "grants", src="r", dst=("l", 0.4), b_label="*")
    d.rel("directory", "member", "dep", "returns", src=("r", 0.5), dst=("b", 0.3),
          via=[(member.x + member.w * 0.3, directory.y + directory.h * 0.5)])
    lane = max(below(b, 18) for b in (impl, policy, pa))
    d.rel("pa", "access", "dep", "uses OrganizationAccess", src="b", dst=("l", 0.8),
          via=[(pa.x + pa.w * 0.5, lane), (-44, lane), (-44, access.y + access.h * 0.8)], label_pos=0.7)
    d.note(right(pa, 30), yi, "Org OWNER / ADMIN get implicit MANAGER on every project "
           "(PROJECT_MANAGE_ALL). Everyone else needs a project membership. The projects team implements "
           "ProjectAccess on top of OrganizationAccess.", width=200)
    return d


def cd_org_internal():
    d = ClassDiagram(LEGEND)
    oc = d.box("oc", "OrganizationController", 0, 0, stereotype="controller  /api/v1/orgs",
               methods=["+ create(CreateOrganizationRequest, AuthenticatedUser): OrganizationResponse",
                        "+ listMine(AuthenticatedUser): List<OrganizationResponse>",
                        "+ get(UUID orgId, AuthenticatedUser): OrganizationResponse",
                        "+ update(UUID orgId, UpdateOrganizationRequest, AuthenticatedUser): OrganizationResponse"])
    mc = d.box("mc", "MemberController", 0, below(oc, 26), stereotype="controller  /api/v1/orgs/{orgId}/members",
               methods=["+ list(UUID orgId, Pageable, AuthenticatedUser): Page<MemberResponse>",
                        "+ changeRole(UUID orgId, UUID userId, ChangeRoleRequest, AuthenticatedUser): MemberResponse",
                        "+ remove(UUID orgId, UUID userId, AuthenticatedUser): void"])
    ic = d.box("ic", "InvitationController", 0, below(mc, 26), stereotype="controller",
               methods=["+ invite(UUID orgId, InviteRequest, AuthenticatedUser): InvitationResponse",
                        "+ listPending(UUID orgId, AuthenticatedUser): List<InvitationResponse>",
                        "+ revoke(UUID orgId, UUID invitationId, AuthenticatedUser): void",
                        "+ accept(AcceptInvitationRequest, AuthenticatedUser): MemberResponse"])
    sx = right(mc, 60)
    os_ = d.box("os", "OrganizationService", sx, 0, stereotype="service",
                methods=["+ create(String name, String slug, UUID ownerId): Organization",
                         "+ listForUser(UUID userId): List<Organization>", "+ get(OrgScope): Organization",
                         "+ update(OrgScope, UpdateOrganizationCommand): Organization"])
    ms = d.box("ms", "MembershipService", sx, below(os_, 26), stereotype="service",
               methods=["+ listMembers(OrgScope, Pageable): Page<MemberSummary>",
                        "+ changeRole(OrgScope, UUID targetUserId, OrgRole newRole): MemberSummary",
                        "+ removeMember(OrgScope, UUID targetUserId): void"])
    is_ = d.box("is", "InvitationService", sx, below(ms, 26), stereotype="service",
                methods=["+ invite(OrgScope, String email, OrgRole role): CreatedInvitation",
                         "+ accept(String rawToken, UUID userId): MemberSummary",
                         "+ revoke(OrgScope, UUID invitationId): void", "+ listPending(OrgScope): List<Invitation>"])
    mailer = d.box("mailer", "InvitationMailer", right(is_, 40), is_.y, kind="interface",
                   methods=["+ send(String email, String orgName, URI acceptLink): void"])
    logm = d.box("logm", "LoggingInvitationMailer", mailer.x, below(mailer, 30), stereotype="dev profile",
                 attrs=["writes the accept link to the log"])
    yd = max(below(ic, 50), below(logm, 40))
    org = d.box("org", "Organization", 0, yd, kind="entity",
                attrs=["- id: UUID", "- name: String", "- slug: String  (unique)", "- timeZone: ZoneId",
                       "- createdAt: Instant", "- version: long"], methods=["+ rename(String): void"])
    mem = d.box("mem", "Membership", right(org, 50), yd, kind="entity",
                attrs=["- id: UUID", "- orgId: UUID", "- userId: UUID", "- role: OrgRole", "- joinedAt: Instant",
                       "- version: long"], methods=["+ changeRole(OrgRole): void"])
    inv = d.box("inv", "Invitation", right(mem, 50), yd, kind="entity",
                attrs=["- id: UUID", "- orgId: UUID", "- email: String", "- role: OrgRole", "- tokenHash: byte[]",
                       "- invitedBy: UUID", "- expiresAt: Instant", "- acceptedAt: Instant", "- revokedAt: Instant"],
                methods=["+ isPending(Instant now): boolean", "+ accept(Instant now): void", "+ revoke(Instant now): void"])
    rx = right(inv, 60)
    orep = d.box("orep", "OrganizationRepository", rx, yd, kind="interface", stereotype="interface  repository",
                 methods=["+ existsBySlug(String): boolean", "+ findAllByIdIn(Collection<UUID>): List<Organization>"])
    mrep = d.box("mrep", "MembershipRepository", rx, below(orep, 20), kind="interface", stereotype="interface  repository",
                 methods=["+ findByOrgIdAndUserId(UUID, UUID): Optional<Membership>",
                          "+ findAllByUserId(UUID): List<Membership>",
                          "+ findAllByOrgId(UUID, Pageable): Page<Membership>",
                          "+ countByOrgIdAndRole(UUID, OrgRole): long"])
    irep = d.box("irep", "InvitationRepository", rx, below(mrep, 20), kind="interface", stereotype="interface  repository",
                 methods=["+ findByTokenHash(byte[]): Optional<Invitation>",
                          "+ findPendingByOrgId(UUID orgId, Instant now): List<Invitation>"])
    d.rel("oc", "os", "assoc", None, src=("r", 0.5), dst=("l", 0.5),
          via=[(oc.x + oc.w + 20, oc.y + oc.h * 0.5), (oc.x + oc.w + 20, os_.y + os_.h * 0.5)])
    d.rel("mc", "ms", "assoc", None, src="r", dst="l")
    d.rel("ic", "is", "assoc", None, src="r", dst="l")
    d.rel("is", "mailer", "assoc", None, src="r", dst="l")
    d.rel("logm", "mailer", "realize", None, src="t", dst="b")
    d.rel("org", "mem", "assoc", None, src=("r", 0.3), dst=("l", 0.3), a_label="1", b_label="*")
    d.rel("inv", "mem", "dep", "accept() creates", src=("l", 0.85), dst=("r", 0.85), label_pos=0.5)
    d.note(sx, below(is_, 25), "Every controller method first calls OrganizationAccess.requireMembership / "
           "requirePermission, then passes the OrgScope to the service. Services never take a raw orgId.",
           width=is_.w)
    return d


def cd_audit():
    d = ClassDiagram(LEGEND)
    trail = d.box("trail", "AuditTrail", 0, 0, kind="interface", stereotype="interface  public API",
                  methods=["+ record(AuditEvent event): void"])
    event = d.box("event", "AuditEvent", 0, below(trail, 30), kind="record",
                  attrs=["action: String", "outcome: AuditOutcome", "orgId: UUID  (nullable)",
                         "actorId: UUID  (nullable)", "targetType: String", "targetId: UUID  (nullable)",
                         "details: Map<String, Object>"],
                  methods=["+ success(String action, UUID orgId, UUID actorId): AuditEvent",
                           "+ denied(String action, UUID orgId, UUID actorId): AuditEvent",
                           "+ failure(String action, UUID orgId, UUID actorId): AuditEvent",
                           "+ withTarget(String type, UUID id): AuditEvent",
                           "+ withDetail(String key, Object value): AuditEvent"])
    outcome = d.box("outcome", "AuditOutcome", right(event, 40), event.y, kind="enum",
                    attrs=["SUCCESS", "DENIED", "FAILURE"])
    actions = d.box("actions", "AuditActions", right(outcome, 50), 0, stereotype="constants",
                    attrs=["AUTH_REGISTERED = \"auth.registered\"",
                           "AUTH_LOGIN_SUCCEEDED = \"auth.login.succeeded\"",
                           "AUTH_LOGIN_FAILED = \"auth.login.failed\"",
                           "AUTH_REFRESH_REUSE_DETECTED = \"auth.refresh.reuse_detected\"",
                           "ORG_CREATED = \"org.created\"", "MEMBER_INVITED = \"org.member.invited\"",
                           "MEMBER_JOINED = \"org.member.joined\"",
                           "MEMBER_ROLE_CHANGED = \"org.member.role_changed\"",
                           "MEMBER_REMOVED = \"org.member.removed\"", "ACCESS_DENIED = \"access.denied\"",
                           "AI_TOOL_DENIED = \"ai.tool.denied\"",
                           "// other teams add e.g. PROJECT_DELETED,", "// DOCUMENT_DELETED"])
    d.group(-14, -24, right(actions, 14) + 14, below(event, 14) + 24, "public API  com.flowpilot.audit", "mine")
    yi = below(event, 60)
    svc = d.box("svc", "AuditTrailService", 0, yi, stereotype="service  @Transactional(REQUIRES_NEW)",
                attrs=["- repository: AuditLogRepository", "- clock: Clock"],
                methods=["+ record(AuditEvent): void  (adds ip, userAgent, requestId)"])
    entry = d.box("entry", "AuditLogEntry", right(svc, 60), yi, kind="entity", stereotype="entity  append-only",
                  attrs=["- id: long", "- orgId: UUID", "- actorId: UUID", "- action: String", "- targetType: String",
                         "- targetId: UUID", "- ip: InetAddress", "- userAgent: String", "- requestId: String",
                         "- outcome: AuditOutcome", "- details: JSON", "- occurredAt: Instant"])
    repo = d.box("repo", "AuditLogRepository", right(entry, 60), yi, kind="interface", stereotype="interface  repository",
                 methods=["+ search(UUID orgId, AuditFilter, Pageable): Page<AuditLogEntry>"])
    query = d.box("query", "AuditQueryService", repo.x, below(repo, 30), stereotype="service",
                  methods=["+ search(OrgScope, AuditFilter, Pageable): Page<AuditEntryResponse>"])
    ctrl = d.box("ctrl", "AuditController", repo.x, below(query, 30),
                 stereotype="controller  GET /api/v1/orgs/{orgId}/audit  (AUDIT_VIEW)",
                 methods=["+ search(UUID orgId, AuditFilter, Pageable, AuthenticatedUser): Page<AuditEntryResponse>"])
    filt = d.box("filt", "AuditFilter", svc.x, below(svc, 40), kind="record",
                 attrs=["action: String", "actorId: UUID", "outcome: AuditOutcome", "from: Instant", "to: Instant"])
    d.rel("svc", "trail", "realize", None, src=("l", 0.4), dst=("l", 0.5),
          via=[(-34, svc.y + svc.h * 0.4), (-34, trail.y + trail.h * 0.5)])
    d.rel("trail", "event", "dep", "takes", src="b", dst="t")
    d.rel("event", "outcome", "assoc", None, src=("r", 0.15), dst=("l", 0.3))
    d.rel("svc", "entry", "dep", "inserts", src=("r", 0.3), dst=("l", 0.15))
    d.rel("repo", "entry", "dep", None, src=("l", 0.4), dst=("r", 0.4))
    d.rel("query", "repo", "assoc", None, src="t", dst="b")
    d.rel("ctrl", "query", "assoc", None, src="t", dst="b")
    d.note(0, below(filt, 20), "How every team records an event:\n"
           "auditTrail.record(AuditEvent.success(AuditActions.PROJECT_DELETED, scope.orgId(), user.userId())"
           ".withTarget(\"project\", projectId));\n"
           "Runs in its own transaction, so DENIED / FAILURE events survive a rollback. The app database role has no "
           "UPDATE/DELETE on audit_log. Never put passwords, tokens or document text in details.",
           width=entry.x - 30)
    return d


def cd_ai_orchestration():
    d = ClassDiagram(LEGEND)
    ctrl = d.box("ctrl", "AnalystController", 0, 0, stereotype="controller  /api/v1/orgs/{orgId}/projects/{projectId}/ai",
                 attrs=["- projectAccess: ProjectAccess", "- analyst: AnalystService"],
                 methods=["+ startConversation(orgId, projectId, StartConversationRequest, user): ConversationResponse",
                          "+ listConversations(orgId, projectId, Pageable, user): Page<ConversationResponse>",
                          "+ getConversation(orgId, projectId, conversationId, user): ConversationDetailResponse",
                          "+ ask(orgId, projectId, conversationId, AskRequest, user): AnalystAnswer",
                          "+ weeklySummary(orgId, projectId, user): AnalystAnswer"])
    svc = d.box("svc", "AnalystService", 0, below(ctrl, 40), stereotype="service  (not @Transactional)",
                attrs=["- quota: AiQuotaService", "- prompts: PromptFactory", "- gateway: AiGateway",
                       "- toolsFactory: ProjectAnalystToolsFactory", "- validator: AnswerValidator",
                       "- metrics: AiMetrics"],
                methods=["+ startConversation(ProjectScope, String title): Conversation",
                         "+ ask(ProjectScope, UUID conversationId, String question): AnalystAnswer",
                         "+ weeklySummary(ProjectScope): AnalystAnswer"])
    quota = d.box("quota", "AiQuotaService", 0, below(svc, 40), stereotype="service  Bucket4j / PostgreSQL",
                  methods=["+ checkAndConsume(UUID userId, UUID orgId): void  (429)"])
    prompts = d.box("prompts", "PromptFactory", 0, below(quota, 24),
                    methods=["+ analystPrompt(String question, List<Message> history): Prompt",
                             "+ weeklySummaryPrompt(): Prompt"])
    gx = right(ctrl, 60)
    gw = d.box("gw", "AiGateway", gx, 0, stereotype="service  timeout + bulkhead + circuit breaker",
               attrs=["- chatClient: ChatClient", "- concurrency: 2 calls per replica",
                      "- timeout: Duration = 120 s", "- maxToolCalls: int = 6"],
               methods=["+ call(Prompt, ProjectAnalystTools): AiCallResult",
                        "  throws AiCapacityExceededException (503),",
                        "  AiTimeoutException (504), AiUnavailableException (503)"])
    chat = d.box("chat", "ChatClient", right(gw, 40), 0, kind="interface", tone="external",
                 stereotype="interface  Spring AI", methods=["+ prompt(Prompt): ChatClientRequestSpec",
                                                            "  .tools(Object...).call().entity(Class<T>)"])
    factory = d.box("factory", "ProjectAnalystToolsFactory", gx, below(gw, 34),
                    methods=["+ create(ProjectScope, EvidenceLedger): ProjectAnalystTools"])
    tools = d.box("tools", "ProjectAnalystTools", gx, below(factory, 34),
                  stereotype="one instance per request",
                  attrs=["- scope: ProjectScope  (bound by the server)", "- ledger: EvidenceLedger",
                         "- recorder: ToolInvocationRecorder"],
                  methods=["@Tool + getProjectOverview(): ProjectOverview",
                           "@Tool + getOverdueTasks(int limit): List<TaskSummary>",
                           "@Tool + getBlockedTasks(int limit): List<BlockedTask>",
                           "@Tool + getMilestoneStatus(String milestoneRef): List<MilestoneStatus>",
                           "@Tool + getTeamWorkload(): List<MemberWorkload>",
                           "@Tool + getRecentProjectActivity(int sinceDays, int limit): List<ActivityItem>",
                           "@Tool + searchProjectDocuments(String query, int topK): List<DocumentPassage>"])
    ledger = d.box("ledger", "EvidenceLedger", right(tools, 40), tools.y, stereotype="per request",
                   methods=["+ record(EvidenceRef ref, String fact): void", "+ recordAll(Collection<?> refs): void",
                            "+ contains(EvidenceRef): boolean", "+ refs(): Set<EvidenceRef>"])
    recorder = d.box("recorder", "ToolInvocationRecorder", ledger.x, below(ledger, 24),
                     methods=["+ record(String tool, Map args, Set<EvidenceRef> refs,", "      boolean authorized, Duration): void"])
    validator = d.box("validator", "AnswerValidator", ledger.x, below(recorder, 24),
                      methods=["+ validate(AnalystResponse, EvidenceLedger): ValidatedAnswer",
                               "  (Bean Validation; removes refs not in the ledger)"])
    metrics = d.box("metrics", "AiMetrics", ledger.x, below(validator, 24),
                    methods=["+ recordRequest(String outcome, Duration latency,", "      int promptTokens, int completionTokens): void"])
    cx = max(right(chat, 40), *(right(b, 60) for b in (ledger, recorder, validator, metrics)))
    contracts = [("pa", "ProjectAccess"), ("pq", "ProjectQueries"), ("tq", "TaskQueries"), ("pm", "ProjectMetrics"),
                 ("aq", "ActivityQueries"), ("ks", "KnowledgeSearch")]
    yy = factory.y
    for id_, name in contracts:
        b = d.box(id_, name, cx, yy, kind="interface", tone="friend", stereotype="interface  other team", width=130)
        yy = below(b, 12)
    d.rel("ctrl", "svc", "assoc", None, src="b", dst="t")
    d.rel("svc", "quota", "assoc", None, src="b", dst="t")
    d.rel("svc", "gw", "assoc", None, src=("r", 0.2), dst=("l", 0.8),
          via=[(svc.x + svc.w + 25, svc.y + svc.h * 0.2), (svc.x + svc.w + 25, gw.y + gw.h * 0.8)])
    d.rel("svc", "factory", "assoc", None, src=("r", 0.45), dst=("l", 0.5),
          via=[(svc.x + svc.w + 40, svc.y + svc.h * 0.45), (svc.x + svc.w + 40, factory.y + factory.h * 0.5)])
    d.rel("svc", "prompts", "assoc", None, src=("l", 0.9), dst=("l", 0.5),
          via=[(-20, svc.y + svc.h * 0.9), (-20, prompts.y + prompts.h * 0.5)])
    d.rel("gw", "chat", "assoc", None, src="r", dst="l")
    d.rel("factory", "tools", "dep", "creates", src="b", dst="t")
    d.rel("tools", "ledger", "assoc", None, src=("r", 0.1), dst=("l", 0.3))
    d.rel("tools", "recorder", "assoc", None, src=("r", 0.3), dst=("l", 0.5))
    for id_, _ in contracts:
        b = d.boxes[id_]
        d.rel("tools", id_, "dep", None, src=("t", 0.97), dst=("l", 0.5),
              via=[(tools.x + tools.w * 0.97, factory.y - 12), (cx - 22, factory.y - 12), (cx - 22, b.y + b.h / 2)])
    d.caption(cx - 150, factory.y - 18, "read-only calls with the bound ProjectScope")
    d.caption(svc.x, below(prompts, 22), "AnalystService also uses AnswerValidator and AiMetrics (see its fields).")
    d.note(gx, below(tools, 20), "The LLM never passes orgId or projectId: the tools object already holds the "
           "ProjectScope. Every tool re-checks ProjectAccess, caps 'limit' at 50, records what it returned in the "
           "EvidenceLedger and is logged by ToolInvocationRecorder. No tool can write data.", width=tools.w)
    return d


def cd_ai_data():
    d = ClassDiagram(LEGEND)
    resp = d.box("resp", "AnalystResponse", 0, 0, kind="record", stereotype="record  structured LLM output",
                 attrs=["summary: String  (max 1500 chars)", "risks: List<Risk>", "evidence: List<Evidence>",
                        "suggestedActions: List<SuggestedAction>", "uncertainties: List<String>"])
    risk = d.box("risk", "Risk", right(resp, 50), 0, kind="record",
                 attrs=["title: String", "severity: Severity", "explanation: String", "evidenceRefs: List<String>"])
    sev = d.box("sev", "Severity", right(risk, 40), 0, kind="enum", attrs=["LOW", "MEDIUM", "HIGH"])
    ev = d.box("ev", "Evidence", risk.x, below(risk, 24), kind="record", attrs=["ref: String", "fact: String"])
    act = d.box("act", "SuggestedAction", risk.x, below(ev, 24), kind="record",
                attrs=["action: String", "rationale: String", "relatedRefs: List<String>"])
    ans = d.box("ans", "AnalystAnswer", 0, below(resp, 40), kind="record", stereotype="record  REST response",
                attrs=["messageId: UUID", "response: AnalystResponse", "verification: Verification", "model: String",
                       "latencyMs: long"])
    ver = d.box("ver", "Verification", 0, below(ans, 24), kind="record",
                attrs=["unverifiedRefs: List<String>", "toolsUsed: List<String>", "repaired: boolean"])
    ref = d.box("ref", "EvidenceRef", right(sev, 60), 0, kind="record",
                attrs=["type: RefType", "key: String"],
                methods=["+ parse(String): EvidenceRef", "+ toString(): \"TASK:FP-42\""])
    rtype = d.box("rtype", "RefType", ref.x, below(ref, 24), kind="enum",
                  attrs=["TASK         TASK:FP-42", "MILESTONE    MILESTONE:FP-M3", "PROJECT      PROJECT:FP",
                         "MEMBER       MEMBER:<userId>", "ACTIVITY     ACTIVITY:<eventId>",
                         "DOCUMENT     DOC:<documentId>#p4"])
    yd = max(below(ver, 50), below(act, 50), below(rtype, 50))
    conv = d.box("conv", "Conversation", 0, yd, kind="entity",
                 attrs=["- id: UUID", "- orgId: UUID", "- projectId: UUID", "- userId: UUID", "- title: String",
                        "- createdAt: Instant"])
    msg = d.box("msg", "ConversationMessage", right(conv, 60), yd, kind="entity",
                attrs=["- id: UUID", "- orgId: UUID", "- conversationId: UUID", "- role: MessageRole",
                       "- content: String", "- structuredResponse: JSON", "- status: MessageStatus", "- model: String",
                       "- promptTokens: int", "- completionTokens: int", "- latencyMs: int", "- verification: JSON"])
    enums = d.box("enums", "MessageRole / MessageStatus", msg.x, below(msg, 24), kind="enum",
                  attrs=["USER, ASSISTANT", "OK, INVALID_OUTPUT, TIMEOUT, REJECTED, ERROR"])
    inv = d.box("inv", "ToolInvocation", right(msg, 60), yd, kind="entity",
                attrs=["- id: UUID", "- orgId: UUID", "- messageId: UUID", "- toolName: String", "- arguments: JSON",
                       "- returnedRefs: JSON", "- authorized: boolean", "- durationMs: int"])
    repos = d.box("repos", "AI repositories", right(inv, 60), yd, stereotype="interfaces  JpaRepository",
                  attrs=["ConversationRepository", "  findByIdAndProjectIdAndUserId(id, projectId, userId)",
                         "  findAllByProjectIdAndUserId(projectId, userId, Pageable)",
                         "ConversationMessageRepository",
                         "  findTop20ByConversationIdOrderByCreatedAtDesc(id)",
                         "ToolInvocationRepository", "  findAllByMessageId(UUID)"])
    d.rel("resp", "risk", "compose", None, src=("r", 0.3), dst=("l", 0.3), b_label="*")
    d.rel("resp", "ev", "compose", None, src=("r", 0.55), dst=("l", 0.5), b_label="*",
          via=[(resp.x + resp.w + 25, resp.y + resp.h * 0.55), (resp.x + resp.w + 25, ev.y + ev.h * 0.5)])
    d.rel("resp", "act", "compose", None, src=("r", 0.8), dst=("l", 0.5), b_label="*",
          via=[(resp.x + resp.w + 15, resp.y + resp.h * 0.8), (resp.x + resp.w + 15, act.y + act.h * 0.5)])
    d.rel("risk", "sev", "assoc", None, src="r", dst=("l", 0.3))
    d.rel("ans", "resp", "assoc", None, src="t", dst="b")
    d.rel("ans", "ver", "compose", None, src="b", dst="t")
    d.rel("ref", "rtype", "assoc", None, src="b", dst="t")
    d.rel("conv", "msg", "compose", None, src=("r", 0.3), dst=("l", 0.3), b_label="*")
    d.rel("msg", "inv", "compose", None, src=("r", 0.3), dst=("l", 0.3), b_label="*")
    d.rel("msg", "enums", "assoc", None, src="b", dst="t")
    d.note(ref.x, below(rtype, 12), "References are the citation format. Other teams' DTOs expose them in a 'ref' "
           "field, and the UI turns them into links.", width=rtype.w + 30)
    return d


def cd_contracts():
    d = ClassDiagram(LEGEND)
    pa = d.box("pa", "ProjectAccess", 0, 0, kind="interface", tone="friend", stereotype="interface  projects team",
               methods=["+ require(UUID orgId, UUID projectId, UUID userId, ProjectPermission): ProjectScope",
                        "    404 if org/project not visible to the user; 403 if permission missing"])
    scope = d.box("scope", "ProjectScope", 0, below(pa, 30), kind="record", tone="friend",
                  attrs=["orgId: UUID", "projectId: UUID", "userId: UUID", "role: ProjectRole",
                         "permissions: Set<ProjectPermission>"], methods=["+ has(ProjectPermission): boolean"])
    prole = d.box("prole", "ProjectRole", right(scope, 40), scope.y, kind="enum", tone="friend",
                  attrs=["MANAGER", "CONTRIBUTOR", "VIEWER"])
    pperm = d.box("pperm", "ProjectPermission", right(prole, 40), scope.y, kind="enum", tone="friend",
                  attrs=["PROJECT_READ", "PROJECT_EDIT", "MEMBER_MANAGE", "TASK_WRITE", "TASK_DELETE",
                         "DOCUMENT_WRITE", "AI_ASK"])
    oa = d.box("oa", "OrganizationAccess", right(pa, 60), 0, kind="interface", stereotype="interface  organizations (Soufiane)",
               methods=["+ requireMembership(orgId, userId): OrgScope", "+ requirePermission(orgId, userId, OrgPermission)"])
    qx = max(right(pperm, 70), right(oa, 70))
    pq = d.box("pq", "ProjectQueries", qx, 0, kind="interface", tone="friend", stereotype="interface  projects team",
               methods=["+ getOverview(ProjectScope): ProjectOverview",
                        "+ getMilestoneStatus(ProjectScope, String milestoneRef): List<MilestoneStatus>",
                        "    milestoneRef null = all open milestones"])
    tq = d.box("tq", "TaskQueries", qx, below(pq, 18), kind="interface", tone="friend", stereotype="interface  tasks team",
               methods=["+ findOverdue(ProjectScope, int limit): List<TaskSummary>",
                        "+ findBlocked(ProjectScope, int limit): List<BlockedTask>"])
    pm = d.box("pm", "ProjectMetrics", qx, below(tq, 18), kind="interface", tone="friend",
               stereotype="interface  analytics team",
               methods=["+ getTeamWorkload(ProjectScope): List<MemberWorkload>", "+ getHealth(ProjectScope): HealthScore"])
    aq = d.box("aq", "ActivityQueries", qx, below(pm, 18), kind="interface", tone="friend",
               stereotype="interface  collaboration team",
               methods=["+ findRecent(ProjectScope, Instant since, int limit): List<ActivityItem>"])
    ks = d.box("ks", "KnowledgeSearch", qx, below(aq, 18), kind="interface", tone="friend",
               stereotype="interface  documents team (Phase 4)",
               methods=["+ search(ProjectScope, String query, int topK): List<DocumentPassage>"])
    tools = d.box("tools", "ProjectAnalystTools", 0, below(scope, 60) + 20, stereotype="ai (Soufiane)  consumer",
                  methods=["calls the interfaces on the right", "with the ProjectScope it holds"])
    d.rel("pa", "scope", "dep", "returns", src="b", dst="t")
    d.rel("scope", "prole", "assoc", None, src=("r", 0.3), dst=("l", 0.3))
    d.rel("prole", "pperm", "assoc", "grants", src=("r", 0.3), dst=("l", 0.3), b_label="*")
    d.rel("pa", "oa", "dep", "uses", src="r", dst="l")
    for id_ in ("pq", "tq", "pm", "aq", "ks"):
        b = d.boxes[id_]
        d.rel("tools", id_, "dep", None, src=("r", 0.5), dst=("l", 0.5),
              via=[(qx - 30, tools.y + tools.h * 0.5), (qx - 30, b.y + b.h * 0.5)])
    d.note(tools.x, below(tools, 24),
           "Rules for every query interface: first parameter is ProjectScope; filter by scope.orgId() AND "
           "scope.projectId(); @Transactional(readOnly = true); return DTO records (never entities); return at "
           "most 'limit' items (hard cap 50); target under 200 ms.", width=280)
    return d


def cd_contract_dtos():
    d = ClassDiagram(LEGEND)
    x = 0
    specs = [
        ("TaskSummary", ["ref: String           \"FP-42\"", "title: String", "status: String", "priority: String",
                         "assigneeName: String", "dueDate: LocalDate", "milestoneRef: String  \"FP-M3\""]),
        ("BlockedTask", ["task: TaskSummary", "blockedByRefs: List<String>"]),
        ("MilestoneStatus", ["ref: String     \"FP-M3\"", "name: String", "dueDate: LocalDate", "status: String",
                             "totalTasks: int", "doneTasks: int", "overdueTasks: int", "blockedTasks: int"]),
        ("ProjectOverview", ["ref: String     \"FP\"", "name: String", "status: String", "startDate: LocalDate",
                             "targetDate: LocalDate", "totalTasks: int", "doneTasks: int", "overdueTasks: int",
                             "blockedTasks: int"]),
    ]
    row1 = []
    for name, attrs in specs:
        b = d.box(name, name, x, 0, kind="record", tone="friend", attrs=attrs)
        row1.append(b)
        x = right(b, 30)
    y2 = max(below(b, 40) for b in row1)
    specs2 = [
        ("MemberWorkload", ["userId: UUID", "displayName: String", "openTasks: int", "openPoints: int",
                            "overdueTasks: int", "capacityPoints: int"]),
        ("ActivityItem", ["occurredAt: Instant", "actorName: String", "action: String",
                          "entityRef: String   \"TASK:FP-42\"", "summary: String"]),
        ("HealthScore", ["score: int    (0..100)", "band: String  GREEN | AMBER | RED",
                         "factors: Map<String, Double>"]),
        ("DocumentPassage", ["ref: String  \"DOC:<id>#p4\"", "documentName: String", "page: int", "text: String",
                             "score: double"]),
    ]
    x = 0
    for name, attrs in specs2:
        b = d.box(name, name, x, y2, kind="record", tone="friend", attrs=attrs)
        x = right(b, 30)
    d.rel("BlockedTask", "TaskSummary", "assoc", None, src="l", dst="r")
    d.note(0, max(below(d.boxes[n], 24) for n, _ in specs2),
           "Field names are the contract. Text fields must be plain text (no HTML). The 'ref' values are stable "
           "human keys (project key + number) and must be the same ones the UI shows, so the AI's citations "
           "can be clicked.", width=520)
    return d


def fe_structure():
    d = ClassDiagram(LEGEND)
    client = d.box("client", "api/client.ts", 0, 0, stereotype="module",
                   methods=["+ apiGet<T>(path, signal?): Promise<T>", "+ apiPost<T>(path, body): Promise<T>",
                            "+ apiPatch<T>(path, body): Promise<T>", "+ apiDelete(path): Promise<void>",
                            "+ class ApiError { status, problem }"])
    store = d.box("store", "auth/tokenStore.ts", 0, below(client, 30), stereotype="module  (memory only)",
                  methods=["+ getAccessToken(): string | null", "+ setAccessToken(token, expiresAt): void",
                           "+ clear(): void"])
    auth = d.box("auth", "AuthProvider + useAuth()", right(client, 60), 0, stereotype="component + hook",
                 attrs=["user: CurrentUser | null", "status: 'loading' | 'authenticated' | 'anonymous'"],
                 methods=["+ login(email, password): Promise<void>", "+ register(input): Promise<void>",
                          "+ logout(): Promise<void>"])
    guard = d.box("guard", "<RequireAuth>", auth.x + 70, below(auth, 30), stereotype="route guard",
                  attrs=["redirects to /login, then back"])
    org = d.box("org", "OrgProvider + useCurrentOrg()", right(auth, 60), 0, stereotype="component + hook",
                attrs=["org: Organization", "role: OrgRole"],
                methods=["+ can(permission: OrgPermission): boolean", "+ switchOrg(orgId): void"])
    switcher = d.box("switcher", "<OrgSwitcher>", org.x, below(org, 30), stereotype="component")
    py = max(below(store, 50), below(guard, 50), below(switcher, 50))
    pages = d.box("pages", "Pages (Soufiane)", 0, py, stereotype="routes",
                  attrs=["/login                 LoginPage", "/register              RegisterPage",
                         "/invitations/accept    AcceptInvitationPage", "/orgs                  OrganizationsPage",
                         "/orgs/:orgId/members   MembersPage", "  InviteMemberDialog, RoleSelect",
                         "/orgs/:orgId/audit     AuditLogPage"])
    ai = d.box("ai", "AI components (Soufiane)", right(pages, 50), py, stereotype="components + hooks",
               attrs=["<AnalystPanel orgId projectId />", "<EvidencePanel answer />",
                      "<ReferenceLink reference=\"TASK:FP-42\" />", "useConversations(orgId, projectId)",
                      "useAskAnalyst(orgId, projectId, conversationId)"])
    routes = d.box("routes", "Routes other teams provide", right(ai, 50), py, tone="friend",
                   stereotype="needed by <ReferenceLink>",
                   attrs=["/orgs/:orgId/projects/:projectId", "/orgs/:orgId/projects/:projectId/tasks/:taskKey",
                          "/orgs/:orgId/projects/:projectId/milestones/:milestoneKey",
                          "/orgs/:orgId/projects/:projectId/documents/:documentId?page=n"])
    d.rel("client", "store", "assoc", "reads token", src="b", dst="t")
    d.rel("auth", "client", "assoc", None, src="l", dst=("r", 0.3))
    d.rel("auth", "store", "assoc", None, src=("b", 0.08), dst="r",
          via=[(auth.x + auth.w * 0.08, store.y + store.h / 2)])
    d.rel("org", "auth", "dep", None, src="l", dst=("r", 0.3))
    d.rel("ai", "routes", "dep", "links to", src="r", dst="l")
    d.note(right(org, 40), 0, "How other teams use this:\n"
           "- call the API only through api/client.ts (adds the Bearer token; on 401 it refreshes once "
           "and retries)\n- wrap pages in <RequireAuth>\n- hide buttons with useCurrentOrg().can(...); "
           "the backend still enforces every permission\n- embed <AnalystPanel orgId projectId /> on the project page",
           width=230)
    return d


# ----------------------------------------------------------------------------- sequence diagrams
def sd_register():
    s = SequenceDiagram([("user", "User", "actor", "neutral"), ("spa", "SPA\nRegisterPage"), ("ctrl", "AuthController"),
                         ("svc", "AuthService"), ("pol", "PasswordPolicy"), ("repo", "UserRepository"),
                         ("enc", "PasswordEncoder\nSpring Security", "box", "external"), ("audit", "AuditTrail")])
    s.msg("user", "spa", "submit email, password, display name")
    s.msg("spa", "ctrl", "POST /api/v1/auth/register {RegisterRequest}")
    s.msg("ctrl", "svc", "register(RegisterCommand)")
    s.msg("svc", "pol", "validate(password)")
    s.begin("alt", "[password shorter than 12 characters]")
    s.ret("pol", "svc", "WeakPasswordException")
    s.ret("ctrl", "spa", "400 application/problem+json")
    s.end()
    s.msg("svc", "repo", "existsByEmailIgnoreCase(email)")
    s.ret("repo", "svc", "boolean")
    s.begin("alt", "[email already registered]")
    s.ret("svc", "ctrl", "EmailAlreadyRegisteredException")
    s.ret("ctrl", "spa", "409 Conflict")
    s.end()
    s.msg("svc", "enc", "encode(password)")
    s.ret("enc", "svc", "Argon2id hash")
    s.msg("svc", "repo", "save(new User(email, hash, displayName))")
    s.ret("repo", "svc", "User")
    s.msg("svc", "audit", "record(AUTH_REGISTERED, SUCCESS)")
    s.self_msg("svc", "publish UserRegisteredEvent")
    s.ret("svc", "ctrl", "UserSummary")
    s.ret("ctrl", "spa", "201 Created {UserResponse}")
    s.ret("spa", "user", "go to the login page")
    return s


def sd_login():
    s = SequenceDiagram([("user", "User", "actor", "neutral"), ("spa", "SPA\nLoginPage"), ("ctrl", "AuthController"),
                         ("svc", "AuthService"), ("lim", "LoginAttemptLimiter"), ("repo", "UserRepository"),
                         ("enc", "PasswordEncoder", "box", "external"), ("jwt", "JwtService"),
                         ("rts", "RefreshTokenService"), ("audit", "AuditTrail")])
    s.msg("user", "spa", "email + password")
    s.msg("spa", "ctrl", "POST /api/v1/auth/login {LoginRequest}")
    s.msg("ctrl", "svc", "login(email, password, ClientInfo)")
    s.msg("svc", "lim", "checkAllowed(email, ip)")
    s.begin("alt", "[too many failed attempts]")
    s.ret("lim", "svc", "TooManyLoginAttemptsException")
    s.ret("ctrl", "spa", "429 + Retry-After")
    s.end()
    s.msg("svc", "repo", "findByEmailIgnoreCase(email)")
    s.ret("repo", "svc", "Optional<User>")
    s.msg("svc", "enc", "matches(password, hash or dummy hash)")
    s.ret("enc", "svc", "boolean")
    s.note("svc", "A dummy hash is checked when the user does not exist, so the response time does not reveal "
                  "which emails are registered.", b="enc")
    s.begin("alt", "[unknown user, disabled user or wrong password]")
    s.msg("svc", "lim", "recordFailure(email, ip)")
    s.msg("svc", "audit", "record(AUTH_LOGIN_FAILED, FAILURE)")
    s.ret("svc", "ctrl", "InvalidCredentialsException")
    s.ret("ctrl", "spa", "401 \"Invalid email or password\" (same message in every case)")
    s.else_("[valid credentials]")
    s.msg("svc", "lim", "reset(email, ip)")
    s.msg("svc", "jwt", "issueAccessToken(user)")
    s.ret("jwt", "svc", "AccessToken (15 min)")
    s.msg("svc", "rts", "issue(user, ClientInfo)")
    s.ret("rts", "svc", "IssuedRefreshToken (new family)")
    s.msg("svc", "audit", "record(AUTH_LOGIN_SUCCEEDED, SUCCESS)")
    s.ret("svc", "ctrl", "AuthTokens")
    s.self_msg("ctrl", "cookies.create(refreshToken)")
    s.ret("ctrl", "spa", "200 {TokenResponse} + Set-Cookie fp_refresh (HttpOnly)")
    s.self_msg("spa", "tokenStore.setAccessToken(...)  (memory only)")
    s.end()
    return s


def sd_refresh():
    s = SequenceDiagram([("spa", "SPA\napi/client.ts"), ("sec", "SecurityFilterChain\nOrigin + header check"),
                         ("ctrl", "AuthController"), ("svc", "AuthService"), ("rts", "RefreshTokenService"),
                         ("repo", "RefreshTokenRepository"), ("jwt", "JwtService"), ("audit", "AuditTrail")])
    s.note("spa", "The access token expired, or an API call returned 401.")
    s.msg("spa", "sec", "POST /api/v1/auth/refresh  (Cookie fp_refresh, X-Requested-With)")
    s.begin("alt", "[Origin not allowed or X-Requested-With missing]")
    s.ret("sec", "spa", "403 (CSRF protection)")
    s.end()
    s.msg("sec", "ctrl", "refresh(refreshCookie)")
    s.msg("ctrl", "svc", "refresh(rawToken, ClientInfo)")
    s.msg("svc", "rts", "rotate(rawToken, ClientInfo)")
    s.self_msg("rts", "hash = SHA-256(rawToken)")
    s.msg("rts", "repo", "findByTokenHash(hash)")
    s.ret("repo", "rts", "Optional<RefreshToken>")
    s.begin("alt", "[not found or expired]")
    s.ret("rts", "svc", "InvalidRefreshTokenException")
    s.ret("ctrl", "spa", "401 + clear cookie  ->  show login page")
    s.else_("[already rotated: the old token was reused, likely stolen]")
    s.msg("rts", "repo", "revokeFamily(familyId, now)")
    s.msg("rts", "audit", "record(AUTH_REFRESH_REUSE_DETECTED, DENIED)")
    s.ret("rts", "svc", "RefreshTokenReusedException")
    s.ret("ctrl", "spa", "401 + clear cookie  (every session of this login is ended)")
    s.else_("[active]")
    s.self_msg("rts", "old.replaceWith(newId, now)")
    s.msg("rts", "repo", "save(old); save(new token, same familyId)")
    s.ret("rts", "svc", "Rotated(user, newToken)")
    s.msg("svc", "jwt", "issueAccessToken(user)")
    s.ret("jwt", "svc", "AccessToken")
    s.ret("svc", "ctrl", "AuthTokens")
    s.ret("ctrl", "spa", "200 {TokenResponse} + Set-Cookie (new refresh token)")
    s.self_msg("spa", "retry the original request once")
    s.end()
    return s


def sd_authorized_request():
    s = SequenceDiagram([("spa", "SPA"), ("filter", "JwtAuthenticationFilter"), ("jwt", "JwtService"),
                         ("ctrl", "TaskController\ntasks team", "box", "friend"),
                         ("pa", "ProjectAccess\nprojects team", "box", "friend"), ("oa", "OrganizationAccess"),
                         ("svc", "TaskQueryService\ntasks team", "box", "friend"),
                         ("repo", "TaskRepository\ntasks team", "box", "friend")])
    s.msg("spa", "filter", "GET /api/v1/orgs/{orgId}/projects/{projectId}/tasks  (Bearer token)")
    s.msg("filter", "jwt", "parseAndValidate(token)")
    s.begin("alt", "[missing, expired or invalid token]")
    s.ret("filter", "spa", "401 problem response")
    s.end()
    s.ret("jwt", "filter", "AuthenticatedUser(userId, email)")
    s.self_msg("filter", "set SecurityContext authentication")
    s.msg("filter", "ctrl", "list(orgId, projectId, filters, AuthenticatedUser)")
    s.msg("ctrl", "pa", "require(orgId, projectId, user.userId(), PROJECT_READ)")
    s.msg("pa", "oa", "requireMembership(orgId, userId)")
    s.begin("alt", "[user is not a member of orgId]")
    s.ret("oa", "pa", "ApiException.notFound")
    s.ret("ctrl", "spa", "404 (never 403: do not reveal that the org exists)")
    s.end()
    s.ret("oa", "pa", "OrgScope(orgId, userId, role)")
    s.self_msg("pa", "load project WHERE id = projectId AND org_id = orgId; resolve project role")
    s.begin("alt", "[project not in this org / permission missing]")
    s.ret("pa", "ctrl", "404 / 403")
    s.end()
    s.ret("pa", "ctrl", "ProjectScope")
    s.msg("ctrl", "svc", "list(scope, filters, pageable)")
    s.msg("svc", "repo", "findPage(scope.orgId(), scope.projectId(), filters, pageable)")
    s.ret("repo", "svc", "Page<Task>")
    s.ret("svc", "ctrl", "Page<TaskSummary>  (DTOs, never entities)")
    s.ret("ctrl", "spa", "200 OK")
    return s


def sd_invite():
    s = SequenceDiagram([("admin", "Org admin", "actor", "neutral"), ("ctrl", "InvitationController"),
                         ("oa", "OrganizationAccess"), ("svc", "InvitationService"), ("pol", "RolePolicy"),
                         ("repo", "InvitationRepository"), ("mail", "InvitationMailer"), ("audit", "AuditTrail")])
    s.msg("admin", "ctrl", "POST /api/v1/orgs/{orgId}/invitations {email, role}")
    s.msg("ctrl", "oa", "requirePermission(orgId, userId, MEMBER_INVITE)")
    s.begin("alt", "[not a member]")
    s.ret("ctrl", "admin", "404")
    s.else_("[member without MEMBER_INVITE]")
    s.ret("ctrl", "admin", "403")
    s.end()
    s.ret("oa", "ctrl", "OrgScope")
    s.msg("ctrl", "svc", "invite(scope, email, role)")
    s.msg("svc", "pol", "canAssign(scope.role(), null, role)")
    s.begin("alt", "[role above the inviter's, e.g. ADMIN inviting an OWNER]")
    s.msg("svc", "audit", "record(MEMBER_INVITED, DENIED)")
    s.ret("ctrl", "admin", "403 Forbidden")
    s.end()
    s.self_msg("svc", "rawToken = 256-bit random; tokenHash = SHA-256(rawToken)")
    s.msg("svc", "repo", "save(Invitation(email, role, tokenHash, expiresAt = now + 7 days))")
    s.msg("svc", "mail", "send(email, orgName, acceptLink containing rawToken)")
    s.msg("svc", "audit", "record(MEMBER_INVITED, SUCCESS)")
    s.ret("svc", "ctrl", "CreatedInvitation")
    s.ret("ctrl", "admin", "201 {InvitationResponse}  (the token is never returned)")
    s.note("mail", "Dev profile: LoggingInvitationMailer writes the accept link to the log.")
    return s


def sd_accept():
    s = SequenceDiagram([("inv", "Invitee", "actor", "neutral"), ("spa", "SPA\nAcceptInvitationPage"),
                         ("ctrl", "InvitationController"), ("svc", "InvitationService"),
                         ("repo", "InvitationRepository"), ("ids", "IdentityApi"), ("mrepo", "MembershipRepository"),
                         ("events", "ApplicationEvent-\nPublisher", "box", "external"), ("audit", "AuditTrail")])
    s.msg("inv", "spa", "open the accept link (log in first if needed)")
    s.msg("spa", "ctrl", "POST /api/v1/invitations/accept {token}")
    s.msg("ctrl", "svc", "accept(rawToken, user.userId())")
    s.msg("svc", "repo", "findByTokenHash(SHA-256(rawToken))")
    s.ret("repo", "svc", "Optional<Invitation>")
    s.begin("alt", "[unknown, expired, revoked or already used]")
    s.ret("ctrl", "spa", "404 Invitation not found")
    s.end()
    s.msg("svc", "ids", "findUser(userId)")
    s.ret("ids", "svc", "UserSummary")
    s.begin("alt", "[logged-in email differs from the invited email]")
    s.ret("ctrl", "spa", "403 This invitation was sent to another email")
    s.else_("[already a member of the organization]")
    s.ret("ctrl", "spa", "409 Conflict")
    s.end()
    s.msg("svc", "mrepo", "save(Membership(orgId, userId, invitation.role))")
    s.self_msg("svc", "invitation.accept(now)")
    s.msg("svc", "events", "publish MemberJoinedEvent(orgId, userId, role)")
    s.msg("svc", "audit", "record(MEMBER_JOINED, SUCCESS)")
    s.ret("svc", "ctrl", "MemberSummary")
    s.ret("ctrl", "spa", "200 {MemberResponse}")
    s.note("events", "The notifications team listens to MemberJoinedEvent.")
    return s


def sd_change_role():
    s = SequenceDiagram([("admin", "Org admin", "actor", "neutral"), ("ctrl", "MemberController"),
                         ("oa", "OrganizationAccess"), ("svc", "MembershipService"), ("repo", "MembershipRepository"),
                         ("pol", "RolePolicy"), ("events", "ApplicationEvent-\nPublisher", "box", "external"),
                         ("audit", "AuditTrail")])
    s.msg("admin", "ctrl", "PATCH /api/v1/orgs/{orgId}/members/{userId} {role, version}")
    s.msg("ctrl", "oa", "requirePermission(orgId, actorId, MEMBER_MANAGE)")
    s.ret("oa", "ctrl", "OrgScope  (404 / 403 as in the invite flow)")
    s.msg("ctrl", "svc", "changeRole(scope, targetUserId, newRole)")
    s.msg("svc", "repo", "findByOrgIdAndUserId(orgId, targetUserId)")
    s.ret("repo", "svc", "Optional<Membership>")
    s.begin("alt", "[target is not a member]")
    s.ret("ctrl", "admin", "404")
    s.end()
    s.msg("svc", "pol", "canAssign(scope.role(), target.role(), newRole)")
    s.begin("alt", "[not allowed, e.g. an ADMIN changing an OWNER]")
    s.msg("svc", "audit", "record(MEMBER_ROLE_CHANGED, DENIED)")
    s.ret("ctrl", "admin", "403")
    s.end()
    s.msg("svc", "pol", "ensureOwnerRemains(orgId, targetUserId)  (when demoting an OWNER)")
    s.begin("alt", "[it would remove the last owner]")
    s.ret("ctrl", "admin", "409 An organization must keep an owner")
    s.end()
    s.self_msg("svc", "membership.changeRole(newRole)")
    s.msg("svc", "repo", "save(membership)  (@Version optimistic lock)")
    s.begin("alt", "[changed concurrently by someone else]")
    s.ret("ctrl", "admin", "409 Conflict: reload and try again")
    s.end()
    s.msg("svc", "events", "publish MemberRoleChangedEvent")
    s.msg("svc", "audit", "record(MEMBER_ROLE_CHANGED, SUCCESS)")
    s.ret("ctrl", "admin", "200 {MemberResponse}")
    return s


def sd_ask_ai():
    s = SequenceDiagram([("user", "Project member", "actor", "neutral"), ("ctrl", "AnalystController"),
                         ("pa", "ProjectAccess\nprojects team", "box", "friend"), ("svc", "AnalystService"),
                         ("gw", "AiGateway"), ("llm", "LLM\nOllama via Spring AI", "box", "external"),
                         ("tools", "ProjectAnalystTools"), ("q", "TaskQueries, ...\nother teams", "box", "friend"),
                         ("val", "AnswerValidator")])
    s.msg("user", "ctrl", "POST .../messages {question}")
    s.msg("ctrl", "pa", "require(..., AI_ASK)")
    s.ret("pa", "ctrl", "ProjectScope")
    s.msg("ctrl", "svc", "ask(scope, convId, question)")
    s.self_msg("svc", "quota.checkAndConsume(userId, orgId)  (AiQuotaService)")
    s.begin("alt", "[quota exceeded]")
    s.ret("ctrl", "user", "429 + Retry-After")
    s.end()
    s.self_msg("svc", "load history, save question (short transaction; connection released)")
    s.self_msg("svc", "ledger = new EvidenceLedger(); tools = toolsFactory.create(scope, ledger)")
    s.msg("svc", "gw", "call(prompt, tools)")
    s.begin("alt", "[2 AI calls already running on this replica]")
    s.ret("ctrl", "user", "503 + Retry-After")
    s.end()
    s.msg("gw", "llm", "prompt + tool definitions  (no DB connection held)")
    s.begin("loop", "[the model requests a tool, at most 6 times]")
    s.msg("llm", "tools", "getOverdueTasks(limit = 20)")
    s.msg("tools", "pa", "require(..., AI_ASK)  (re-checked on every tool call)")
    s.msg("tools", "q", "findOverdue(scope, 20)")
    s.ret("q", "tools", "List<TaskSummary>")
    s.self_msg("tools", "ledger.recordAll(tasks)  (refs FP-12, FP-17 -> EvidenceLedger)")
    s.ret("tools", "llm", "JSON tool result (untrusted data)")
    s.end()
    s.ret("llm", "gw", "structured JSON answer")
    s.begin("alt", "[timeout (120 s) or provider down]")
    s.ret("ctrl", "user", "504 / 503 \"AI temporarily unavailable\"")
    s.end()
    s.ret("gw", "svc", "AiCallResult")
    s.msg("svc", "val", "validate(response, ledger)")
    s.begin("alt", "[invalid JSON or schema]")
    s.msg("svc", "gw", "retry once with the validation errors")
    s.ret("ctrl", "user", "502 \"The AI returned an invalid answer\"  (if the retry also fails)")
    s.end()
    s.ret("val", "svc", "ValidatedAnswer  (unknown refs removed and flagged)")
    s.self_msg("svc", "save answer, tool calls, tokens, latency; AiMetrics.recordRequest(...)")
    s.ret("svc", "ctrl", "AnalystAnswer")
    s.ret("ctrl", "user", "200 {summary, risks, evidence, suggestedActions, uncertainties, verification}")
    return s


# ============================================================================= DOCUMENT
def on_page(canvas, doc):
    w, h = landscape(A4)
    canvas.saveState()
    canvas.setFont(FONT, 7.8)
    canvas.setFillColor(colors.HexColor("#6B7280"))
    if doc.page > 1:
        canvas.drawString(16 * mm, h - 9 * mm, DOC_TITLE)
        canvas.drawRightString(w - 16 * mm, h - 9 * mm, f"v{VERSION}  |  {DATE}")
        canvas.setStrokeColor(colors.HexColor("#E5E7EB"))
        canvas.setLineWidth(0.6)
        canvas.line(16 * mm, h - 11 * mm, w - 16 * mm, h - 11 * mm)
    canvas.drawString(16 * mm, 8 * mm, "Owner: Soufiane  |  Draft for team review: names are the contract, propose changes by PR")
    canvas.drawRightString(w - 16 * mm, 8 * mm, f"Page {doc.page}")
    canvas.restoreState()


def diagram_page(story, number, title, intro, diagram):
    story.append(P(f"{number}  {title}", "h1"))
    story.append(P(intro, "intro"))
    story.append(diagram)
    story.append(PageBreak())


def build():
    doc = BaseDocTemplate(OUT, pagesize=landscape(A4), leftMargin=16 * mm, rightMargin=16 * mm,
                          topMargin=15 * mm, bottomMargin=13 * mm, title=DOC_TITLE, author="Soufiane",
                          subject="UML class, sequence and use-case diagrams")
    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="f", leftPadding=0, rightPadding=0,
                  topPadding=0, bottomPadding=0)
    doc.addPageTemplates([PageTemplate(id="p", frames=[frame], onPage=on_page)])
    story = []

    # ---- cover
    story.append(Spacer(1, 40))
    story.append(P("FlowPilot AI", "title"))
    story.append(P("UML design: Identity, Organizations, Roles &amp; Permissions, Audit and AI integration", "subtitle"))
    story.append(Spacer(1, 18))
    story.append(table([
        ["Item", "Value"],
        ["Owner", "Soufiane (initial codebase creator)"],
        ["Modules covered", code("identity") + ", " + code("organizations") + ", " + code("audit") + ", "
         + code("ai") + ", " + code("shared.security") + ", and the matching frontend parts"],
        ["Contents", "Module map, 2 use-case diagrams, 9 class diagrams, 8 sequence diagrams, permission matrices, "
                     "REST endpoints, frontend structure"],
        ["Purpose", "Tell the other teams the exact class, method and endpoint names they will call, and the "
                    "interfaces they must implement for the AI analyst"],
        ["Version / date", f"{VERSION} / {DATE}  (design before implementation: Phase 2.2 onwards)"],
    ], [120, 520]))
    story.append(Spacer(1, 16))
    story.append(P("How to read this document", "h2"))
    for text in [
        "<b>Colours</b>: blue = Soufiane's modules, orange = interfaces or classes another team implements, "
        "green = shared kernel, purple = framework or external system (Spring, Ollama).",
        "<b>Arrows</b>: solid line with open arrow = uses / has a reference; dashed open arrow = depends on, returns "
        "or creates; dashed line with hollow triangle = implements; filled diamond = composition (part of).",
        "<b>Packages</b>: everything lives in " + code("com.flowpilot.&lt;module&gt;") + ". Only the module's root "
        "package is public (the interfaces and records marked \"public API\"). Classes in " + code("internal.*")
        + " must not be used by other modules; a Spring Modulith test fails the build if they are.",
        "<b>Sequence diagrams</b>: messages are numbered; solid arrows are calls, dashed arrows are returns. "
        + code("alt") + " = alternatives, " + code("loop") + " = repetition.",
    ]:
        story.append(Paragraph(text, styles["bullet"], bulletText="•"))
    story.append(PageBreak())

    # ---- contract rules
    story.append(P("0  Rules every team follows", "h1"))
    story.append(P("These rules are what keep tenants isolated and the AI's answers verifiable. They apply to "
                   "every module, not only to Soufiane's.", "intro"))
    for text in [
        "<b>Resolve access first.</b> Every endpoint under " + code("/api/v1/orgs/{orgId}/...") + " starts with "
        + code("OrganizationAccess.requireMembership/requirePermission") + " or, for project data, "
        + code("ProjectAccess.require(orgId, projectId, userId, permission)") + ". Never trust an "
        + code("orgId") + " or " + code("projectId") + " sent in a request body.",
        "<b>Pass the scope, not ids.</b> Service and query methods on project data take " + code("ProjectScope")
        + " (or " + code("OrgScope") + ") as their first parameter, and every SQL query filters by "
        + code("scope.orgId()") + " AND " + code("scope.projectId()") + ".",
        "<b>404, not 403, across tenants.</b> If the user is not a member of the organization, answer 404 "
        "(" + code("ApiException.notFound(...)") + ") so ids cannot be probed. 403 is only for members missing a permission.",
        "<b>The caller</b> is always " + code("@AuthenticationPrincipal AuthenticatedUser user") + ". Roles are "
        "not in the JWT; they are checked on every request.",
        "<b>Return DTO records, never JPA entities.</b> Records the AI may cite carry a stable " + code("ref")
        + " (e.g. " + code("FP-42") + ", " + code("FP-M3") + ") that the UI also shows.",
        "<b>AI query interfaces are read-only</b> (" + code("@Transactional(readOnly = true)") + "), return at most "
        + code("limit") + " items (hard cap 50) and should answer in under 200 ms.",
        "<b>Audit sensitive actions</b> with " + code("AuditTrail.record(...)") + " (see the audit diagram). "
        "Never put passwords, tokens, API keys or document text in audit details or logs.",
        "<b>Listen to membership events.</b> The projects team removes project memberships on "
        + code("MemberRemovedEvent") + "; the notifications team reacts to " + code("MemberJoinedEvent") + ".",
        "<b>Time</b> comes from the injected " + code("Clock") + "; errors are " + code("ApiException")
        + " subclasses, rendered as RFC 9457 problem responses by the shared handler.",
        "<b>Names here are the contract.</b> To rename or change a signature, open a pull request that updates "
        "this document first.",
    ]:
        story.append(Paragraph(text, styles["bullet"], bulletText="•"))
    story.append(PageBreak())

    diagram_page(story, "1", "Module map and ownership",
                 "Who owns which module and which public interfaces each module exposes. Arrows point from the "
                 "module that uses an interface to the module that provides it.", module_overview())
    diagram_page(story, "2", "Use cases: identity and organizations",
                 "What visitors, users, members, admins and owners can do. Sensitive use cases include recording an "
                 "audit entry.", uc_identity())
    diagram_page(story, "3", "Use cases: AI Project Analyst",
                 "Asking a question always includes the quota check, the permission check, read-only data retrieval "
                 "through tools, answer generation and evidence validation.", uc_ai())
    diagram_page(story, "4", "Class diagram: authentication and tokens (identity)",
                 "Registration, login, refresh-token rotation and logout. Access tokens are short-lived JWTs; "
                 "refresh tokens are random values stored only as SHA-256 hashes and rotated on every use.",
                 cd_identity_auth())
    diagram_page(story, "5", "Class diagram: security filter, domain, persistence and public API (identity)",
                 "How a request becomes an AuthenticatedUser, the entities and repositories, and the IdentityApi "
                 "other modules may call.", cd_identity_core())
    diagram_page(story, "6", "Class diagram: organizations public API and RBAC",
                 "The interfaces and types other modules use to check organization access and roles. "
                 "OrganizationAccess is the single place where organization membership is decided.", cd_org_api())
    diagram_page(story, "7", "Class diagram: organizations internals",
                 "Controllers, services, entities and repositories for organizations, members and invitations "
                 "(internal: not for use by other modules).", cd_org_internal())
    diagram_page(story, "8", "Class diagram: audit",
                 "A single synchronous API, AuditTrail.record(AuditEvent), used by every module for security-relevant "
                 "operations, plus the org-scoped audit log query.", cd_audit())
    diagram_page(story, "9", "Class diagram: AI analyst orchestration and tools",
                 "How a question becomes a validated, evidence-backed answer. The orange interfaces are implemented "
                 "by the other teams (details in sections 11 and 12).", cd_ai_orchestration())
    diagram_page(story, "10", "Class diagram: AI response model and persistence",
                 "The structured answer the LLM must return, the REST response, the citation format and the stored "
                 "conversations.", cd_ai_data())
    diagram_page(story, "11", "Class diagram: interfaces other teams implement for access control and the AI",
                 "These are the contracts the AI analyst depends on. Each team implements its interface in the root "
                 "package of its module.", cd_contracts())
    diagram_page(story, "12", "Class diagram: DTO records returned by the AI query interfaces",
                 "Implemented by the other teams. The AI reads these fields, and the UI uses the refs to build links.",
                 cd_contract_dtos())

    sequences = [
        ("Register", "Creating an account. Passwords are validated, hashed with Argon2id, and the event is audited.",
         sd_register()),
        ("Log in", "Rate-limited login that never reveals whether an email is registered. Issues a 15-minute access "
                   "token and a refresh cookie.", sd_login()),
        ("Refresh the session (rotation and reuse detection)",
         "Every refresh replaces the refresh token. Presenting an already-used token ends the whole login family.",
         sd_refresh()),
        ("Authorized request: the pattern every module follows",
         "Example with the tasks team's endpoint: authenticate, resolve ProjectScope through ProjectAccess and "
         "OrganizationAccess, then query with the scope.", sd_authorized_request()),
        ("Invite a member", "Only members with MEMBER_INVITE can invite, and never to a role above their own.",
         sd_invite()),
        ("Accept an invitation", "The invitation token is single-use, expires after 7 days and only works for the "
                                 "invited email.", sd_accept()),
        ("Change a member's role", "Role rules, last-owner protection and optimistic locking.", sd_change_role()),
        ("Ask the AI Project Analyst",
         "Authorization, quota, bounded concurrency, tool calling with evidence recording, and validation of the "
         "structured answer.", sd_ask_ai()),
    ]
    for k, (title, intro, diagram) in enumerate(sequences, start=13):
        diagram_page(story, str(k), f"Sequence diagram: {title}", intro, diagram)

    # ---- permission matrices
    story.append(P("21  Roles and permissions", "h1"))
    story.append(P("Organization roles map to organization permissions (OrgRole.permissions()). Project roles map to "
                   "project permissions (implemented by the projects team). Org OWNER and ADMIN have the MANAGER "
                   "project role on every project in the organization.", "intro"))
    dot, no = "●", "–"
    org_rows = [["OrgPermission", "OWNER", "ADMIN", "PROJECT_MANAGER", "MEMBER", "Used by"],
                ["ORG_UPDATE", dot, dot, no, no, "PATCH /orgs/{orgId}"],
                ["ORG_DELETE", dot, no, no, no, "(later phase)"],
                ["MEMBER_INVITE", dot, dot, no, no, "invitations endpoints"],
                ["MEMBER_MANAGE", dot, dot + " (not owners)", no, no, "change role, remove member"],
                ["PROJECT_CREATE", dot, dot, dot, no, "POST /orgs/{orgId}/projects (projects team)"],
                ["PROJECT_MANAGE_ALL", dot, dot, no, no, "implicit MANAGER on every project"],
                ["AUDIT_VIEW", dot, dot, no, no, "GET /orgs/{orgId}/audit"]]
    story.append(table(org_rows, [130, 60, 85, 100, 60, 300], mono_cols=(0,), center_cols=(1, 2, 3, 4)))
    story.append(Spacer(1, 14))
    proj_rows = [["ProjectPermission", "MANAGER", "CONTRIBUTOR", "VIEWER", "Meaning"],
                 ["PROJECT_READ", dot, dot, dot, "see the project, tasks, milestones, analytics"],
                 ["PROJECT_EDIT", dot, no, no, "edit project details and milestones"],
                 ["MEMBER_MANAGE", dot, no, no, "add / remove project members (must be org members)"],
                 ["TASK_WRITE", dot, dot, no, "create and edit tasks, comment"],
                 ["TASK_DELETE", dot, dot + " (own tasks)", no, "delete tasks"],
                 ["DOCUMENT_WRITE", dot, dot, no, "upload documents; contributors delete only their own"],
                 ["AI_ASK", dot, dot, dot, "use the AI analyst and the knowledge hub"]]
    story.append(table(proj_rows, [130, 70, 95, 70, 370], mono_cols=(0,), center_cols=(1, 2, 3)))
    story.append(PageBreak())

    # ---- endpoints
    story.append(P("22  REST endpoints owned by Soufiane", "h1"))
    story.append(P("Base path " + code("/api/v1") + ". JSON bodies; errors are " + code("application/problem+json")
                   + ". " + code("...") + " = " + code("/api/v1/orgs/{orgId}/projects/{projectId}") + ".", "intro"))
    ep = [["Method", "Path", "Handler", "Access"],
          ["POST", "/api/v1/auth/register", "AuthController.register", "public"],
          ["POST", "/api/v1/auth/login", "AuthController.login", "public, rate-limited"],
          ["POST", "/api/v1/auth/refresh", "AuthController.refresh", "refresh cookie + X-Requested-With"],
          ["POST", "/api/v1/auth/logout", "AuthController.logout", "refresh cookie"],
          ["GET", "/api/v1/me", "MeController.me", "authenticated"],
          ["POST / GET", "/api/v1/orgs", "OrganizationController.create / listMine", "authenticated"],
          ["GET / PATCH", "/api/v1/orgs/{orgId}", "OrganizationController.get / update", "member / ORG_UPDATE"],
          ["GET", "/api/v1/orgs/{orgId}/members", "MemberController.list", "member"],
          ["PATCH / DELETE", "/api/v1/orgs/{orgId}/members/{userId}", "MemberController.changeRole / remove",
           "MEMBER_MANAGE (members may remove themselves)"],
          ["POST / GET", "/api/v1/orgs/{orgId}/invitations", "InvitationController.invite / listPending", "MEMBER_INVITE"],
          ["DELETE", "/api/v1/orgs/{orgId}/invitations/{invitationId}", "InvitationController.revoke", "MEMBER_INVITE"],
          ["POST", "/api/v1/invitations/accept", "InvitationController.accept", "authenticated, invited email"],
          ["GET", "/api/v1/orgs/{orgId}/audit", "AuditController.search", "AUDIT_VIEW"],
          ["POST / GET", ".../ai/conversations", "AnalystController.startConversation / listConversations",
           "AI_ASK (own conversations only)"],
          ["GET", ".../ai/conversations/{conversationId}", "AnalystController.getConversation", "AI_ASK, owner only"],
          ["POST", ".../ai/conversations/{conversationId}/messages", "AnalystController.ask", "AI_ASK"],
          ["POST", ".../ai/weekly-summary", "AnalystController.weeklySummary", "AI_ASK"]]
    story.append(table(ep, [75, 250, 245, 205], mono_cols=(1, 2)))
    story.append(PageBreak())

    diagram_page(story, "23", "Frontend structure (Soufiane's parts and what other teams reuse)",
                 "Shared frontend building blocks: the API client, authentication, the current organization and the AI "
                 "components. Other teams' pages reuse them instead of writing their own.", fe_structure())

    story.append(P("24  Open points for the team", "h1"))
    for text in [
        "Confirm the query interface names and DTO fields in sections 11 and 12, or propose changes by pull request "
        "before Phase 3 starts.",
        "Projects team: confirm the human key format (" + code("FP-42") + " for tasks, " + code("FP-M3")
        + " for milestones) and that keys never change once assigned.",
        "Frontend: agree on the route paths in section 23, because the AI's clickable citations depend on them.",
        "Phase 4: the documents team owns " + code("KnowledgeSearch") + "; the AI only calls it.",
    ]:
        story.append(Paragraph(text, styles["bullet"], bulletText="•"))

    doc.build(story)
    print(f"written {OUT}")


if __name__ == "__main__":
    build()
