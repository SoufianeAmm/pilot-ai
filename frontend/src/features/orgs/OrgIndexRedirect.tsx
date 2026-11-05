import { Navigate } from 'react-router'
import { errorMessage } from '../../api/errors'
import { FormAlert } from '../../components/form'
import { CreateOrganizationPage } from './CreateOrganizationPage'
import { lastOrg } from './lastOrg'
import { useMyOrgs } from './queries'

/** "/": back to the organization used last time, or onboarding when the user has none yet. */
export function OrgIndexRedirect() {
  const orgs = useMyOrgs()

  if (orgs.isPending) {
    return <p role="status" className="py-16 text-center text-sm text-slate-500">Loading your organizations…</p>
  }
  if (orgs.isError) {
    return <FormAlert>{errorMessage(orgs.error)}</FormAlert>
  }
  if (orgs.data.length === 0) {
    return <CreateOrganizationPage onboarding />
  }
  const remembered = lastOrg.get()
  const target = orgs.data.find((org) => org.id === remembered) ?? orgs.data[0]
  return <Navigate to={`/orgs/${target.id}`} replace />
}
