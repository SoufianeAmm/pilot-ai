import { createContext, useContext } from 'react'
import type { Organization } from './orgApi'

export const OrgContext = createContext<Organization | null>(null)

/** The organization of the current /orgs/:orgId page, with the signed-in user's role in it. */
export function useCurrentOrg(): Organization {
  const org = useContext(OrgContext)
  if (org === null) {
    throw new Error('useCurrentOrg must be used inside <OrgLayout>')
  }
  return org
}
