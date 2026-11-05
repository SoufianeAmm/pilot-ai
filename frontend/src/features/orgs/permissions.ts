import type { OrgRole } from './orgApi'

/**
 * A copy of the backend rules (OrgRole and RolePolicy), used only to decide which buttons to show.
 * The backend still checks every request: hiding a button is a convenience, not security.
 */

export type OrgPermission =
  | 'ORG_UPDATE'
  | 'ORG_DELETE'
  | 'MEMBER_INVITE'
  | 'MEMBER_MANAGE'
  | 'PROJECT_CREATE'
  | 'PROJECT_MANAGE_ALL'
  | 'AUDIT_VIEW'

/** From most to least powerful. */
export const ORG_ROLES: OrgRole[] = ['OWNER', 'ADMIN', 'PROJECT_MANAGER', 'MEMBER']

export const ROLE_LABELS: Record<OrgRole, string> = {
  OWNER: 'Owner',
  ADMIN: 'Admin',
  PROJECT_MANAGER: 'Project manager',
  MEMBER: 'Member',
}

const RANK: Record<OrgRole, number> = { OWNER: 3, ADMIN: 2, PROJECT_MANAGER: 1, MEMBER: 0 }

const PERMISSIONS: Record<OrgRole, ReadonlySet<OrgPermission>> = {
  OWNER: new Set<OrgPermission>([
    'ORG_UPDATE', 'ORG_DELETE', 'MEMBER_INVITE', 'MEMBER_MANAGE', 'PROJECT_CREATE', 'PROJECT_MANAGE_ALL', 'AUDIT_VIEW',
  ]),
  ADMIN: new Set<OrgPermission>([
    'ORG_UPDATE', 'MEMBER_INVITE', 'MEMBER_MANAGE', 'PROJECT_CREATE', 'PROJECT_MANAGE_ALL', 'AUDIT_VIEW',
  ]),
  PROJECT_MANAGER: new Set<OrgPermission>(['PROJECT_CREATE']),
  MEMBER: new Set<OrgPermission>(),
}

export function hasPermission(role: OrgRole, permission: OrgPermission): boolean {
  return PERMISSIONS[role].has(permission)
}

function outranks(a: OrgRole, b: OrgRole): boolean {
  return RANK[a] > RANK[b]
}

/** RolePolicy.canAssign: owners assign anything; others never above themselves and never touch an owner. */
export function canAssign(actor: OrgRole, currentTarget: OrgRole | null, newRole: OrgRole): boolean {
  if (actor === 'OWNER') {
    return true
  }
  if (!hasPermission(actor, 'MEMBER_MANAGE') && !hasPermission(actor, 'MEMBER_INVITE')) {
    return false
  }
  return !outranks(newRole, actor) && currentTarget !== 'OWNER'
}

/** The roles the actor may give: to a new invitee (currentTarget null) or to an existing member. */
export function assignableRoles(actor: OrgRole, currentTarget: OrgRole | null): OrgRole[] {
  return ORG_ROLES.filter((role) => canAssign(actor, currentTarget, role))
}

/** MembershipService.changeRole: needs MEMBER_MANAGE, then at least one other role must be assignable. */
export function canChangeRole(actor: OrgRole, target: OrgRole): boolean {
  return hasPermission(actor, 'MEMBER_MANAGE') && assignableRoles(actor, target).some((role) => role !== target)
}

/** RolePolicy.canRemove. Leaving (removing yourself) is always allowed and handled separately. */
export function canRemove(actor: OrgRole, target: OrgRole): boolean {
  if (actor === 'OWNER') {
    return true
  }
  return hasPermission(actor, 'MEMBER_MANAGE') && target !== 'OWNER'
}
