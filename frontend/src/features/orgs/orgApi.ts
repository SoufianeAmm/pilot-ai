import { apiDelete, apiGet, apiPatch, apiPost } from '../../api/client'

export type OrgRole = 'OWNER' | 'ADMIN' | 'PROJECT_MANAGER' | 'MEMBER'

export interface Organization {
  id: string
  name: string
  slug: string
  timeZone: string
  /** The signed-in user's role in this organization. */
  role: OrgRole
  createdAt: string
  version: number
}

export interface Member {
  userId: string
  displayName: string
  email: string
  role: OrgRole
  joinedAt: string
  version: number
}

export interface Invitation {
  id: string
  email: string
  role: OrgRole
  invitedBy: string
  createdAt: string
  expiresAt: string
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export const MEMBERS_PAGE_SIZE = 20

export const orgApi = {
  listMine: (signal?: AbortSignal) => apiGet<Organization[]>('/orgs', signal),

  get: (orgId: string, signal?: AbortSignal) => apiGet<Organization>(`/orgs/${orgId}`, signal),

  create: (name: string) => apiPost<Organization>('/orgs', { name }),

  update: (orgId: string, changes: { name?: string; timeZone?: string; version: number }) =>
    apiPatch<Organization>(`/orgs/${orgId}`, changes),

  members: (orgId: string, page: number, signal?: AbortSignal) =>
    apiGet<Page<Member>>(`/orgs/${orgId}/members?page=${page}&size=${MEMBERS_PAGE_SIZE}`, signal),

  changeRole: (orgId: string, userId: string, role: OrgRole, version: number) =>
    apiPatch<Member>(`/orgs/${orgId}/members/${userId}`, { role, version }),

  removeMember: (orgId: string, userId: string) => apiDelete(`/orgs/${orgId}/members/${userId}`),

  invitations: (orgId: string, signal?: AbortSignal) => apiGet<Invitation[]>(`/orgs/${orgId}/invitations`, signal),

  invite: (orgId: string, email: string, role: OrgRole) =>
    apiPost<Invitation>(`/orgs/${orgId}/invitations`, { email, role }),

  revokeInvitation: (orgId: string, invitationId: string) => apiDelete(`/orgs/${orgId}/invitations/${invitationId}`),

  acceptInvitation: (token: string) => apiPost<Organization>('/invitations/accept', { token }),
}
