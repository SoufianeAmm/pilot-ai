import { tokenStore } from './tokenStore'

/** RFC 9457 problem response, as returned by the backend's GlobalExceptionHandler. */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  requestId?: string
  errors?: { field: string; message: string }[]
}

export class ApiError extends Error {
  readonly status: number
  readonly problem?: ProblemDetail
  /** Seconds to wait, from the Retry-After header of a 429 response. */
  readonly retryAfter?: number

  constructor(status: number, problem?: ProblemDetail, retryAfter?: number) {
    super(problem?.detail ?? problem?.title ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
    this.retryAfter = retryAfter
  }

  /** The validation message the backend gave for one field, if any. */
  fieldError(field: string): string | undefined {
    return this.problem?.errors?.find((error) => error.field === field)?.message
  }
}

export interface TokenResponse {
  accessToken: string
  expiresIn: number
  tokenType: string
}

const API_BASE = '/api/v1'

/**
 * Endpoints authenticated by the refresh cookie need this header: the backend rejects them
 * without it, which a cross-site form cannot send (CSRF protection).
 */
const COOKIE_REQUEST_HEADERS = { 'X-Requested-With': 'XMLHttpRequest' }

interface RequestOptions {
  body?: unknown
  signal?: AbortSignal
  headers?: Record<string, string>
}

export function apiGet<T>(path: string, signal?: AbortSignal): Promise<T> {
  return apiRequest<T>('GET', path, { signal })
}

export function apiPost<T>(path: string, body?: unknown, options: Omit<RequestOptions, 'body'> = {}): Promise<T> {
  return apiRequest<T>('POST', path, { ...options, body })
}

export function apiPatch<T>(path: string, body: unknown, options: Omit<RequestOptions, 'body'> = {}): Promise<T> {
  return apiRequest<T>('PATCH', path, { ...options, body })
}

export function apiDelete<T = void>(path: string, options: Omit<RequestOptions, 'body'> = {}): Promise<T> {
  return apiRequest<T>('DELETE', path, options)
}

/**
 * Sends a request with the current access token. If the token has expired (401), it refreshes the
 * session once and retries, so a 15-minute token expiry is invisible to the user. Auth endpoints
 * are never retried: a 401 from /auth/login means wrong credentials, not an expired token.
 */
export async function apiRequest<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const tokenUsed = tokenStore.get()
  let response = await send(method, path, options, tokenUsed)

  if (response.status === 401 && tokenUsed !== null && !path.startsWith('/auth/')) {
    const refreshed = await refreshSessionOrExpire(tokenUsed)
    if (refreshed) {
      response = await send(method, path, options, tokenStore.get())
    }
  }
  return parse<T>(response)
}

let refreshInFlight: Promise<TokenResponse> | null = null

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share one request: the
 * backend rotates the refresh token on every use, so two parallel refreshes would look like a
 * stolen-token replay and end the session.
 */
export function refreshSession(): Promise<TokenResponse> {
  refreshInFlight ??= apiRequest<TokenResponse>('POST', '/auth/refresh', { headers: COOKIE_REQUEST_HEADERS })
    .then((tokens) => {
      tokenStore.set(tokens.accessToken)
      return tokens
    })
    .finally(() => {
      refreshInFlight = null
    })
  return refreshInFlight
}

export async function logoutSession(): Promise<void> {
  try {
    await apiRequest<void>('POST', '/auth/logout', { headers: COOKIE_REQUEST_HEADERS })
  } finally {
    tokenStore.set(null)
  }
}

async function refreshSessionOrExpire(tokenUsed: string): Promise<boolean> {
  // Another request may have refreshed while this one was in flight.
  if (tokenStore.get() !== null && tokenStore.get() !== tokenUsed) {
    return true
  }
  try {
    await refreshSession()
    return true
  } catch {
    tokenStore.expire()
    return false
  }
}

function send(method: string, path: string, options: RequestOptions, token: string | null): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (token !== null) {
    headers.Authorization = `Bearer ${token}`
  }
  return fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  })
}

async function parse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const retryAfter = Number(response.headers.get('Retry-After')) || undefined
    throw new ApiError(response.status, await readProblem(response), retryAfter)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

async function readProblem(response: Response): Promise<ProblemDetail | undefined> {
  const contentType = response.headers.get('Content-Type') ?? ''
  if (!contentType.includes('json')) {
    return undefined
  }
  try {
    return (await response.json()) as ProblemDetail
  } catch {
    return undefined
  }
}
