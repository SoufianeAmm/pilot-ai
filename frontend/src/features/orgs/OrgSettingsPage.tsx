import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState, type FormEvent } from 'react'
import { errorMessage, isConflict } from '../../api/errors'
import { Button, FormAlert, SelectField, SuccessNote, TextField } from '../../components/form'
import { orgApi } from './orgApi'
import { useCurrentOrg } from './orgContext'
import { hasPermission } from './permissions'
import { orgKeys } from './queries'

const NAME_MAX_LENGTH = 100

export function OrgSettingsPage() {
  const org = useCurrentOrg()
  if (!hasPermission(org.role, 'ORG_UPDATE')) {
    return <p className="text-sm text-slate-600">Only owners and admins can change the organization settings.</p>
  }
  // A fresh form per organization, so switching organizations never shows the previous one's values.
  return <SettingsForm key={org.id} />
}

function SettingsForm() {
  const org = useCurrentOrg()
  const queryClient = useQueryClient()
  const [name, setName] = useState(org.name)
  const [timeZone, setTimeZone] = useState(org.timeZone)
  const [fieldError, setFieldError] = useState<string>()
  const [saved, setSaved] = useState(false)
  const timeZones = useMemo(() => timeZoneOptions(org.timeZone), [org.timeZone])

  const save = useMutation({
    mutationFn: () => orgApi.update(org.id, { name: name.trim(), timeZone, version: org.version }),
    onSuccess: async (updated) => {
      queryClient.setQueryData(orgKeys.detail(org.id), updated)
      await queryClient.invalidateQueries({ queryKey: orgKeys.mine() })
      setSaved(true)
    },
    onError: async (error) => {
      if (isConflict(error)) {
        // Someone else saved first: show their values and take their version, so the next save can succeed.
        const latest = await queryClient.fetchQuery({
          queryKey: orgKeys.detail(org.id),
          queryFn: ({ signal }) => orgApi.get(org.id, signal),
          staleTime: 0,
        })
        setName(latest.name)
        setTimeZone(latest.timeZone)
      }
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaved(false)
    const trimmed = name.trim()
    if (!trimmed || trimmed.length > NAME_MAX_LENGTH) {
      setFieldError(trimmed ? `Use at most ${NAME_MAX_LENGTH} characters.` : 'Enter a name.')
      return
    }
    setFieldError(undefined)
    save.mutate()
  }

  const unchanged = name.trim() === org.name && timeZone === org.timeZone

  return (
    <section aria-labelledby="settings-heading" className="max-w-lg rounded-lg border border-slate-200 bg-white p-5">
      <h2 id="settings-heading" className="font-semibold">Organization settings</h2>
      <form onSubmit={handleSubmit} className="mt-4 space-y-4" noValidate>
        {save.isError && (
          <FormAlert>
            {isConflict(save.error)
              ? 'Someone else changed these settings just now. The latest values are loaded, review them and save again.'
              : errorMessage(save.error)}
          </FormAlert>
        )}
        {saved && <SuccessNote>Settings saved.</SuccessNote>}
        <TextField
          label="Name"
          value={name}
          onChange={(event) => {
            setName(event.target.value)
            setFieldError(undefined)
            setSaved(false)
          }}
          error={fieldError}
        />
        <SelectField
          label="Time zone"
          value={timeZone}
          onChange={(event) => {
            setTimeZone(event.target.value)
            setSaved(false)
          }}
          options={timeZones.map((zone) => ({ value: zone, label: zone.replaceAll('_', ' ') }))}
        />
        <p className="text-xs text-slate-500">Used for due dates and reports. Address: /{org.slug}</p>
        <Button type="submit" disabled={save.isPending || unchanged}>
          {save.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </form>
    </section>
  )
}

function timeZoneOptions(current: string): string[] {
  const zones = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : []
  const all = new Set(['UTC', ...zones])
  all.add(current)
  return [...all]
}
