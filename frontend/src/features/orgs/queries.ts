import { useQuery } from '@tanstack/react-query'
import { orgApi } from './orgApi'

export const orgKeys = {
  all: ['orgs'] as const,
  mine: () => [...orgKeys.all, 'mine'] as const,
  detail: (orgId: string) => [...orgKeys.all, orgId] as const,
  members: (orgId: string) => [...orgKeys.detail(orgId), 'members'] as const,
  membersPage: (orgId: string, page: number) => [...orgKeys.members(orgId), page] as const,
  invitations: (orgId: string) => [...orgKeys.detail(orgId), 'invitations'] as const,
}

export function useMyOrgs() {
  return useQuery({ queryKey: orgKeys.mine(), queryFn: ({ signal }) => orgApi.listMine(signal) })
}

export function useOrg(orgId: string) {
  return useQuery({ queryKey: orgKeys.detail(orgId), queryFn: ({ signal }) => orgApi.get(orgId, signal) })
}

export function useMembers(orgId: string, page: number) {
  return useQuery({
    queryKey: orgKeys.membersPage(orgId, page),
    queryFn: ({ signal }) => orgApi.members(orgId, page, signal),
    placeholderData: (previous) => previous,
  })
}

export function useInvitations(orgId: string, enabled: boolean) {
  return useQuery({
    queryKey: orgKeys.invitations(orgId),
    queryFn: ({ signal }) => orgApi.invitations(orgId, signal),
    enabled,
  })
}
