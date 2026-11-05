import { describe, expect, it } from 'vitest'
import { redirectTarget } from './redirect'
import { validateRegistration } from './registerValidation'

const VALID = {
  displayName: 'Soufiane',
  email: 'soufiane@example.com',
  password: 'correct horse battery',
  confirmPassword: 'correct horse battery',
}

describe('validateRegistration', () => {
  it('accepts valid input', () => {
    expect(validateRegistration(VALID)).toEqual({})
  })

  it('applies the backend rules', () => {
    const errors = validateRegistration({ displayName: ' ', email: 'nope', password: 'short', confirmPassword: 'x' })

    expect(errors.displayName).toBe('Enter your name.')
    expect(errors.email).toBe('Enter a valid email address.')
    expect(errors.password).toBe('Use at least 12 characters.')
    expect(errors.confirmPassword).toBe('The passwords do not match.')
  })

  it('rejects passwords longer than 128 characters', () => {
    const long = 'a'.repeat(129)
    expect(validateRegistration({ ...VALID, password: long, confirmPassword: long }).password).toBe(
      'Use at most 128 characters.',
    )
  })
})

describe('redirectTarget', () => {
  it('returns to the page that required sign-in', () => {
    expect(redirectTarget({ from: { pathname: '/orgs/1', search: '?tab=members' } })).toBe('/orgs/1?tab=members')
  })

  it('falls back to home without a usable origin', () => {
    expect(redirectTarget(null)).toBe('/')
    expect(redirectTarget({ from: { pathname: '//evil.example', search: '' } })).toBe('/')
    expect(redirectTarget({ from: { pathname: 'https://evil.example', search: '' } })).toBe('/')
  })
})
