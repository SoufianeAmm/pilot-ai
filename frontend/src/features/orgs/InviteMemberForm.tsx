import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/errors'
import { Button, FormAlert, SelectField, SuccessNote, TextField } from '../../components/form'
import { orgApi, type OrgRole } from './orgApi'
import { useCurrentOrg } from './orgContext'
import { assignableRoles, ROLE_LABELS } from './permissions'
import { orgKeys } from './queries'

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function InviteMemberForm() {
  const org = useCurrentOrg()
  const queryClient = useQueryClient()
  const roles = assignableRoles(org.role, null)
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<OrgRole>('MEMBER')
  const [fieldError, setFieldError] = useState<string>()
  const [sentTo, setSentTo] = useState<string>()

  const invite = useMutation({
    mutationFn: (input: { email: string; role: OrgRole }) => orgApi.invite(org.id, input.email, input.role),
    onSuccess: (invitation) => {
      setSentTo(invitation.email)
      setEmail('')
      void queryClient.invalidateQueries({ queryKey: orgKeys.invitations(org.id) })
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSentTo(undefined)
    if (!EMAIL.test(email.trim())) {
      setFieldError('Enter a valid email address.')
      return
    }
    setFieldError(undefined)
    invite.mutate({ email: email.trim(), role })
  }

  return (
    <section aria-labelledby="invite-heading" className="rounded-lg border border-slate-200 bg-white p-5">
      <h2 id="invite-heading" className="font-semibold">Invite a member</h2>
      <p className="mt-1 text-sm text-slate-600">They get a link that is valid for 7 days.</p>
      <form onSubmit={handleSubmit} className="mt-4 space-y-3" noValidate>
        {invite.isError && <FormAlert>{errorMessage(invite.error)}</FormAlert>}
        {sentTo && <SuccessNote>Invitation sent to {sentTo}.</SuccessNote>}
        <div className="grid gap-3 sm:grid-cols-[1fr_12rem_auto] sm:items-start">
          <TextField
            label="Email"
            type="email"
            autoComplete="off"
            placeholder="colleague@example.com"
            value={email}
            onChange={(event) => {
              setEmail(event.target.value)
              setFieldError(undefined)
            }}
            error={fieldError}
          />
          <SelectField
            label="Role"
            value={role}
            onChange={(event) => setRole(event.target.value as OrgRole)}
            options={roles.map((value) => ({ value, label: ROLE_LABELS[value] }))}
          />
          <Button type="submit" className="sm:mt-6" disabled={invite.isPending}>
            {invite.isPending ? 'Sending…' : 'Send invite'}
          </Button>
        </div>
      </form>
    </section>
  )
}
