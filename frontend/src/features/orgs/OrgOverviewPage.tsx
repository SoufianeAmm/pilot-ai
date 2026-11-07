import { Link } from 'react-router'
import { SystemStatus } from '../system/SystemStatus'
import { useCurrentOrg } from './orgContext'
import { hasPermission } from './permissions'

export function OrgOverviewPage() {
  const org = useCurrentOrg()
  const canInvite = hasPermission(org.role, 'MEMBER_INVITE')

  return (
    <div className="space-y-6">
      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="font-semibold">Get started</h2>
        <p className="mt-1 text-sm text-slate-600">
          Projects, tasks and the AI analyst will appear here as they are built.
        </p>
        {canInvite && (
          <p className="mt-3 text-sm">
            <Link to="members" className="font-medium text-indigo-600 hover:underline">Invite your team</Link>
            {' '}so they can join {org.name}.
          </p>
        )}
      </section>
      <SystemStatus />
    </div>
  )
}
