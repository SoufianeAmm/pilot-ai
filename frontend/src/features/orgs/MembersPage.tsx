import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { errorMessage, isConflict } from '../../api/errors'
import { Button, FormAlert, SelectField } from '../../components/form'
import { useCurrentUser } from '../auth/authContext'
import { InviteMemberForm } from './InviteMemberForm'
import { lastOrg } from './lastOrg'
import { orgApi, type Member, type OrgRole } from './orgApi'
import { useCurrentOrg } from './orgContext'
import { PendingInvitations } from './PendingInvitations'
import { assignableRoles, canChangeRole, canRemove, hasPermission, ROLE_LABELS } from './permissions'
import { orgKeys, useMembers } from './queries'

export function MembersPage() {
  const org = useCurrentOrg()
  const canInvite = hasPermission(org.role, 'MEMBER_INVITE')

  return (
    <div className="space-y-6">
      {canInvite && <InviteMemberForm />}
      {canInvite && <PendingInvitations />}
      <MemberList />
    </div>
  )
}

function MemberList() {
  const org = useCurrentOrg()
  const [page, setPage] = useState(0)
  const members = useMembers(org.id, page)
  const [actionError, setActionError] = useState<string>()

  return (
    <section aria-labelledby="members-heading" className="rounded-lg border border-slate-200 bg-white">
      <div className="flex items-baseline justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <h2 id="members-heading" className="font-semibold">Members</h2>
        {members.data && <p className="text-sm text-slate-500">{members.data.totalElements} in total</p>}
      </div>
      {actionError && <div className="px-5 pt-4"><FormAlert>{actionError}</FormAlert></div>}
      {members.isPending && <p role="status" className="px-5 py-6 text-sm text-slate-500">Loading members…</p>}
      {members.isError && <div className="p-5"><FormAlert>{errorMessage(members.error)}</FormAlert></div>}
      {members.data && (
        <ul className="divide-y divide-slate-100">
          {members.data.items.map((member) => (
            <MemberRow key={member.userId} member={member} onError={setActionError} />
          ))}
        </ul>
      )}
      {members.data && members.data.totalPages > 1 && (
        <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3 text-sm">
          <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
          <span className="text-slate-500">Page {page + 1} of {members.data.totalPages}</span>
          <Button
            variant="secondary"
            disabled={page + 1 >= members.data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      )}
    </section>
  )
}

function MemberRow({ member, onError }: { member: Member; onError: (message: string | undefined) => void }) {
  const org = useCurrentOrg()
  const user = useCurrentUser()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const isSelf = member.userId === user.id

  const refresh = () => queryClient.invalidateQueries({ queryKey: orgKeys.members(org.id) })

  const changeRole = useMutation({
    mutationFn: (role: OrgRole) => orgApi.changeRole(org.id, member.userId, role, member.version),
    onMutate: () => onError(undefined),
    onError: (error) =>
      onError(isConflict(error) && /changed by someone else/i.test(errorMessage(error))
        ? 'Someone else changed this member just now. The list has been refreshed, try again.'
        : errorMessage(error)),
    onSettled: refresh,
  })

  const remove = useMutation({
    mutationFn: () => orgApi.removeMember(org.id, member.userId),
    onMutate: () => onError(undefined),
    onError: (error) => onError(errorMessage(error)),
    onSuccess: async () => {
      if (isSelf) {
        // Left the organization: its pages are no longer accessible.
        lastOrg.clear()
        queryClient.removeQueries({ queryKey: orgKeys.detail(org.id) })
        await queryClient.invalidateQueries({ queryKey: orgKeys.mine() })
        navigate('/', { replace: true })
        return
      }
      await refresh()
    },
  })

  function handleRemove() {
    const question = isSelf
      ? `Leave ${org.name}? You will lose access until someone invites you again.`
      : `Remove ${member.displayName} from ${org.name}?`
    if (window.confirm(question)) {
      remove.mutate()
    }
  }

  const editable = !isSelf && canChangeRole(org.role, member.role)
  const roleOptions = editable ? withCurrent(assignableRoles(org.role, member.role), member.role) : []

  return (
    <li className="flex flex-wrap items-center gap-3 px-5 py-3">
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">
          {member.displayName}
          {isSelf && <span className="ml-2 text-xs font-normal text-slate-500">(you)</span>}
        </p>
        <p className="truncate text-xs text-slate-500">{member.email}</p>
      </div>
      {editable ? (
        <SelectField
          label={`Role of ${member.displayName}`}
          hideLabel
          className="w-44"
          value={member.role}
          disabled={changeRole.isPending}
          onChange={(event) => changeRole.mutate(event.target.value as OrgRole)}
          options={roleOptions.map((value) => ({ value, label: ROLE_LABELS[value] }))}
        />
      ) : (
        <span className="w-44 text-sm text-slate-700">{ROLE_LABELS[member.role]}</span>
      )}
      <div className="w-24 text-right">
        {isSelf ? (
          <Button variant="secondary" onClick={handleRemove} disabled={remove.isPending}>Leave</Button>
        ) : (
          canRemove(org.role, member.role) && (
            <Button
              variant="danger"
              onClick={handleRemove}
              disabled={remove.isPending}
              aria-label={`Remove ${member.displayName}`}
            >
              Remove
            </Button>
          )
        )}
      </div>
    </li>
  )
}

/** The dropdown always shows the member's current role, even when the actor could not assign it. */
function withCurrent(roles: OrgRole[], current: OrgRole): OrgRole[] {
  return roles.includes(current) ? roles : [current, ...roles]
}
