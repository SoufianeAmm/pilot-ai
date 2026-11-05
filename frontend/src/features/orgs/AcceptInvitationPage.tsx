import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { errorMessage } from '../../api/errors'
import { Button, FormAlert } from '../../components/form'
import { useAuth, useCurrentUser } from '../auth/authContext'
import { AuthCard } from '../auth/AuthCard'
import { orgApi } from './orgApi'
import { orgKeys } from './queries'

/**
 * Opened from the invitation email. Signed-out visitors are sent to log in first and come back here
 * with the token. Joining needs a click: a link preview or a crawler opening the URL must not accept.
 */
export function AcceptInvitationPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const user = useCurrentUser()
  const { logout } = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const accept = useMutation({
    mutationFn: (value: string) => orgApi.acceptInvitation(value),
    onSuccess: async (org) => {
      queryClient.setQueryData(orgKeys.detail(org.id), org)
      await queryClient.invalidateQueries({ queryKey: orgKeys.mine() })
      navigate(`/orgs/${org.id}`, { replace: true })
    },
  })

  if (!token) {
    return (
      <AuthCard title="Invalid invitation link" subtitle="The link is incomplete. Open it again from the email.">
        <Link to="/" className="text-sm font-medium text-indigo-600 hover:underline">Go to FlowPilot</Link>
      </AuthCard>
    )
  }

  return (
    <AuthCard
      title="Join an organization"
      subtitle="You have been invited to FlowPilot AI."
      footer={
        <>
          Invited with another email?{' '}
          <button type="button" onClick={() => void logout()} className="font-medium text-indigo-600 hover:underline">
            Sign in with a different account
          </button>
        </>
      }
    >
      <div className="space-y-4">
        {accept.isError && <FormAlert>{errorMessage(accept.error)}</FormAlert>}
        <p className="text-sm text-slate-600">
          You are signed in as <strong className="font-medium text-slate-900">{user.email}</strong>. The invitation
          must have been sent to this address.
        </p>
        <Button className="w-full" onClick={() => accept.mutate(token)} disabled={accept.isPending}>
          {accept.isPending ? 'Joining…' : 'Accept invitation'}
        </Button>
      </div>
    </AuthCard>
  )
}
