import { ApiError } from './client'

/** A message a person can act on, for any error thrown by an API call. */
export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Cannot reach the server. Check your connection and try again.'
  }
  if (error.status >= 500) {
    return 'Something went wrong on our side. Please try again.'
  }
  return error.problem?.detail ?? error.message
}

export function isConflict(error: unknown): boolean {
  return error instanceof ApiError && error.status === 409
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}
