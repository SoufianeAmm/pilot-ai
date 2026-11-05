import { useMatch, useNavigate } from 'react-router'
import { useMyOrgs } from './queries'

const NEW_ORG = '__new__'

/** Header dropdown: the user's organizations, plus a shortcut to create one. */
export function OrgSwitcher() {
  const orgs = useMyOrgs()
  const navigate = useNavigate()
  const match = useMatch('/orgs/:orgId/*')
  const currentId = match?.params.orgId

  if (!orgs.data || orgs.data.length === 0) {
    return null
  }
  const known = orgs.data.some((org) => org.id === currentId)

  return (
    <div>
      <label htmlFor="org-switcher" className="sr-only">Organization</label>
      <select
        id="org-switcher"
        value={known ? currentId : ''}
        onChange={(event) => {
          const value = event.target.value
          navigate(value === NEW_ORG ? '/orgs/new' : `/orgs/${value}`)
        }}
        className="max-w-48 truncate rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm shadow-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200"
      >
        {!known && <option value="" disabled>Choose an organization</option>}
        {orgs.data.map((org) => (
          <option key={org.id} value={org.id}>{org.name}</option>
        ))}
        <option value={NEW_ORG}>+ New organization</option>
      </select>
    </div>
  )
}
