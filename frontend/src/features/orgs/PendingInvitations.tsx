import { useMutation, useQueryClient } from '@tanstack/react-query'
import { errorMessage } from '../../api/errors'
import { Button, FormAlert } from '../../components/form'
import { orgApi, type Invitation } from './orgApi'
import { useCurrentOrg } from './orgContext'
import { ROLE_LABELS } from './permissions'
import { orgKeys, useInvitations } from './queries'

export function PendingInvitations() {
  const org = useCurrentOrg()
  const queryClient = useQueryClient()
  const invitations = useInvitations(org.id, true)

  const revoke = useMutation({
    mutationFn: (invitation: Invitation) => orgApi.revokeInvitation(org.id, invitation.id),
    onSettled: () => queryClient.invalidateQueries({ queryKey: orgKeys.invitations(org.id) }),
  })

  function handleRevoke(invitation: Invitation) {
    if (window.confirm(`Revoke the invitation for ${invitation.email}? The link will stop working.`)) {
      revoke.mutate(invitation)
    }
  }

  if (invitations.isPending || (invitations.isSuccess && invitations.data.length === 0)) {
    return null
  }

  return (
    <section aria-labelledby="pending-heading" className="rounded-lg border border-slate-200 bg-white p-5">
      <h2 id="pending-heading" className="font-semibold">Pending invitations</h2>
      {invitations.isError && <FormAlert>{errorMessage(invitations.error)}</FormAlert>}
      {revoke.isError && <div className="mt-3"><FormAlert>{errorMessage(revoke.error)}</FormAlert></div>}
      <ul className="mt-3 divide-y divide-slate-100">
        {invitations.data?.map((invitation) => (
          <li key={invitation.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{invitation.email}</p>
              <p className="text-xs text-slate-500">
                {ROLE_LABELS[invitation.role]} · expires {formatDate(invitation.expiresAt)}
              </p>
            </div>
            <Button
              variant="secondary"
              onClick={() => handleRevoke(invitation)}
              disabled={revoke.isPending}
              aria-label={`Revoke invitation for ${invitation.email}`}
            >
              Revoke
            </Button>
          </li>
        ))}
      </ul>
    </section>
  )
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}
