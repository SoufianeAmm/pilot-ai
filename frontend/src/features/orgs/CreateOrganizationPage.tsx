import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { errorMessage } from '../../api/errors'
import { Button, FormAlert, TextField } from '../../components/form'
import { useCurrentUser } from '../auth/authContext'
import { AuthCard } from '../auth/AuthCard'
import { orgApi } from './orgApi'
import { orgKeys } from './queries'

const NAME_MAX_LENGTH = 100

/** @param onboarding true when the user has no organization yet (shown on "/") */
export function CreateOrganizationPage({ onboarding = false }: { onboarding?: boolean }) {
  const user = useCurrentUser()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [fieldError, setFieldError] = useState<string>()

  const create = useMutation({
    mutationFn: (orgName: string) => orgApi.create(orgName),
    onSuccess: async (org) => {
      queryClient.setQueryData(orgKeys.detail(org.id), org)
      await queryClient.invalidateQueries({ queryKey: orgKeys.mine() })
      navigate(`/orgs/${org.id}`)
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) {
      setFieldError('Enter a name for your organization.')
      return
    }
    if (trimmed.length > NAME_MAX_LENGTH) {
      setFieldError(`Use at most ${NAME_MAX_LENGTH} characters.`)
      return
    }
    setFieldError(undefined)
    create.mutate(trimmed)
  }

  return (
    <AuthCard
      title={onboarding ? `Welcome, ${user.displayName}` : 'Create an organization'}
      subtitle={
        onboarding
          ? 'Create your first organization to start planning projects with your team.'
          : 'An organization holds your team, projects and documents.'
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {create.isError && <FormAlert>{errorMessage(create.error)}</FormAlert>}
        <TextField
          label="Organization name"
          placeholder="Acme Corp"
          value={name}
          onChange={(event) => {
            setName(event.target.value)
            setFieldError(undefined)
          }}
          error={fieldError}
          hint="You can rename it later."
        />
        <Button type="submit" className="w-full" disabled={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create organization'}
        </Button>
      </form>
    </AuthCard>
  )
}
