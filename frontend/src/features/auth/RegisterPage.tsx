import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../api/client'
import { Button, FormAlert, TextField } from '../../components/form'
import { AuthCard } from './AuthCard'
import { PASSWORD_MIN_LENGTH } from './authApi'
import { useAuth } from './authContext'
import { authErrorMessage } from './errors'
import { redirectTarget } from './redirect'
import { validateRegistration, type FieldErrors, type Fields } from './registerValidation'

const EMPTY: Fields = { displayName: '', email: '', password: '', confirmPassword: '' }

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [fields, setFields] = useState<Fields>(EMPTY)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function update(name: keyof Fields) {
    return (event: { target: { value: string } }) => {
      setFields((current) => ({ ...current, [name]: event.target.value }))
      setFieldErrors((current) => ({ ...current, [name]: undefined }))
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    const errors = validateRegistration(fields)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      return
    }
    setSubmitting(true)
    try {
      await register({ displayName: fields.displayName.trim(), email: fields.email.trim(), password: fields.password })
      navigate(redirectTarget(location.state), { replace: true })
    } catch (caught) {
      showServerError(caught)
    } finally {
      setSubmitting(false)
    }
  }

  function showServerError(caught: unknown) {
    if (caught instanceof ApiError && caught.status === 409) {
      setFieldErrors({ email: 'An account with this email already exists.' })
      return
    }
    if (caught instanceof ApiError && caught.problem?.errors?.length) {
      setFieldErrors({
        displayName: caught.fieldError('displayName'),
        email: caught.fieldError('email'),
        password: caught.fieldError('password'),
      })
      return
    }
    setError(authErrorMessage(caught))
  }

  return (
    <AuthCard
      title="Create your account"
      subtitle="Start planning with FlowPilot AI."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" state={location.state} className="font-medium text-indigo-600 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {error && <FormAlert>{error}</FormAlert>}
        <TextField
          label="Name"
          autoComplete="name"
          value={fields.displayName}
          onChange={update('displayName')}
          error={fieldErrors.displayName}
        />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          value={fields.email}
          onChange={update('email')}
          error={fieldErrors.email}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          value={fields.password}
          onChange={update('password')}
          error={fieldErrors.password}
          hint={`At least ${PASSWORD_MIN_LENGTH} characters. A long passphrase is easiest to remember.`}
        />
        <TextField
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          value={fields.confirmPassword}
          onChange={update('confirmPassword')}
          error={fieldErrors.confirmPassword}
        />
        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </Button>
      </form>
    </AuthCard>
  )
}
