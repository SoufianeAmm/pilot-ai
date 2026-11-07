import { useEffect } from 'react'
import { Link, NavLink, Outlet, useParams } from 'react-router'
import { errorMessage, isNotFound } from '../../api/errors'
import { FormAlert } from '../../components/form'
import { lastOrg } from './lastOrg'
import { OrgContext } from './orgContext'
import { hasPermission, ROLE_LABELS } from './permissions'
import { useOrg } from './queries'

/** Everything under /orgs/:orgId: loads the organization once and shares it with the pages below. */
export function OrgLayout() {
  const { orgId = '' } = useParams()
  const org = useOrg(orgId)

  useEffect(() => {
    if (org.data) {
      lastOrg.set(org.data.id)
    } else if (isNotFound(org.error)) {
      lastOrg.clear()
    }
  }, [org.data, org.error])

  if (org.isPending) {
    return <p role="status" className="py-16 text-center text-sm text-slate-500">Loading organization…</p>
  }
  if (org.isError) {
    return (
      <div className="mx-auto max-w-md space-y-4 py-8 text-center">
        <h1 className="text-xl font-semibold">
          {isNotFound(org.error) ? 'Organization not found' : 'Could not load the organization'}
        </h1>
        {!isNotFound(org.error) && <FormAlert>{errorMessage(org.error)}</FormAlert>}
        <Link to="/" className="text-sm font-medium text-indigo-600 hover:underline">Go to your organizations</Link>
      </div>
    )
  }

  const tabs = [
    { to: '.', label: 'Overview', end: true },
    { to: 'members', label: 'Members', end: false },
    ...(hasPermission(org.data.role, 'ORG_UPDATE') ? [{ to: 'settings', label: 'Settings', end: false }] : []),
  ]

  return (
    <OrgContext.Provider value={org.data}>
      <div className="space-y-6">
        <div className="space-y-4 border-b border-slate-200">
          <div>
            <h1 className="text-2xl font-semibold">{org.data.name}</h1>
            <p className="text-sm text-slate-500">You are {article(ROLE_LABELS[org.data.role])} here.</p>
          </div>
          <nav aria-label="Organization" className="-mb-px flex gap-6 overflow-x-auto">
            {tabs.map((tab) => (
              <NavLink
                key={tab.label}
                to={tab.to}
                end={tab.end}
                className={({ isActive }) =>
                  `whitespace-nowrap border-b-2 pb-2 text-sm font-medium ${
                    isActive ? 'border-indigo-600 text-indigo-700' : 'border-transparent text-slate-600 hover:text-slate-900'
                  }`
                }
              >
                {tab.label}
              </NavLink>
            ))}
          </nav>
        </div>
        <Outlet />
      </div>
    </OrgContext.Provider>
  )
}

function article(label: string): string {
  return `${/^[AEIOU]/i.test(label) ? 'an' : 'a'} ${label.toLowerCase()}`
}
