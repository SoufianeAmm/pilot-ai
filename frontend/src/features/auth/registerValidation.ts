import { DISPLAY_NAME_MAX_LENGTH, PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from './authApi'

export interface Fields {
  displayName: string
  email: string
  password: string
  confirmPassword: string
}

export type FieldErrors = Partial<Record<keyof Fields, string>>

/** The same rules the backend enforces, checked first so the user gets instant feedback. */
export function validateRegistration(fields: Fields): FieldErrors {
  const errors: FieldErrors = {}
  const name = fields.displayName.trim()
  if (!name) {
    errors.displayName = 'Enter your name.'
  } else if (name.length > DISPLAY_NAME_MAX_LENGTH) {
    errors.displayName = `Use at most ${DISPLAY_NAME_MAX_LENGTH} characters.`
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(fields.email.trim())) {
    errors.email = 'Enter a valid email address.'
  }
  if (fields.password.length < PASSWORD_MIN_LENGTH) {
    errors.password = `Use at least ${PASSWORD_MIN_LENGTH} characters.`
  } else if (fields.password.length > PASSWORD_MAX_LENGTH) {
    errors.password = `Use at most ${PASSWORD_MAX_LENGTH} characters.`
  }
  if (fields.confirmPassword !== fields.password) {
    errors.confirmPassword = 'The passwords do not match.'
  }
  return errors
}
