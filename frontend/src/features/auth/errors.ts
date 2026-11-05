import { ApiError } from '../../api/client'

/** A message a person can act on, for any error thrown by the auth calls. */
export function authErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Cannot reach the server. Check your connection and try again.'
  }
  if (error.status === 429) {
    const minutes = error.retryAfter ? Math.ceil(error.retryAfter / 60) : undefined
    return minutes
      ? `Too many failed attempts. Try again in ${minutes} minute${minutes === 1 ? '' : 's'}.`
      : 'Too many failed attempts. Try again later.'
  }
  if (error.status >= 500) {
    return 'Something went wrong on our side. Please try again.'
  }
  return error.problem?.detail ?? error.message
}
