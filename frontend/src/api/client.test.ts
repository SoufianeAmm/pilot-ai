import { beforeEach, describe, expect, it } from 'vitest'
import { header, json, mockApi, problem } from '../test/mockApi'
import { ApiError, apiGet, apiPost, logoutSession, refreshSession } from './client'
import { tokenStore } from './tokenStore'

const TOKENS = { accessToken: 'new-token', expiresIn: 900, tokenType: 'Bearer' }

describe('api client', () => {
  beforeEach(() => {
    tokenStore.set(null)
  })

  it('sends the access token as a bearer header', async () => {
    tokenStore.set('token-1')
    const api = mockApi({ 'GET /me': () => json({ id: '1' }) })

    await apiGet('/me')

    expect(header(api.calls('GET /me')[0], 'Authorization')).toBe('Bearer token-1')
  })

  it('refreshes an expired token once and retries the request', async () => {
    tokenStore.set('expired')
    let meCalls = 0
    const api = mockApi({
      'GET /me': () => (++meCalls === 1 ? problem(401, 'expired') : json({ id: '1' })),
      'POST /auth/refresh': () => json(TOKENS),
    })

    await expect(apiGet('/me')).resolves.toEqual({ id: '1' })

    const [first, retry] = api.calls('GET /me')
    expect(header(first, 'Authorization')).toBe('Bearer expired')
    expect(header(retry, 'Authorization')).toBe('Bearer new-token')
    expect(tokenStore.get()).toBe('new-token')
  })

  it('sends the CSRF header on refresh', async () => {
    const api = mockApi({ 'POST /auth/refresh': () => json(TOKENS) })

    await refreshSession()

    expect(header(api.calls('POST /auth/refresh')[0], 'X-Requested-With')).toBe('XMLHttpRequest')
  })

  it('shares one refresh between requests that expire at the same time', async () => {
    tokenStore.set('expired')
    const api = mockApi({
      'GET /a': (init) => (header(init, 'Authorization') === 'Bearer new-token' ? json('a') : problem(401, 'x')),
      'GET /b': (init) => (header(init, 'Authorization') === 'Bearer new-token' ? json('b') : problem(401, 'x')),
      'POST /auth/refresh': async () => {
        await new Promise((resolve) => setTimeout(resolve, 10))
        return json(TOKENS)
      },
    })

    await expect(Promise.all([apiGet('/a'), apiGet('/b')])).resolves.toEqual(['a', 'b'])

    // A second refresh would reuse the rotated cookie, which the backend treats as token theft.
    expect(api.calls('POST /auth/refresh')).toHaveLength(1)
  })

  it('ends the session when the refresh fails', async () => {
    tokenStore.set('expired')
    let expired = 0
    const unsubscribe = tokenStore.onExpired(() => expired++)
    mockApi({
      'GET /me': () => problem(401, 'expired'),
      'POST /auth/refresh': () => problem(401, 'Your session has expired.'),
    })

    await expect(apiGet('/me')).rejects.toMatchObject({ status: 401 })

    expect(tokenStore.get()).toBeNull()
    expect(expired).toBe(1)
    unsubscribe()
  })

  it('never refreshes on a failed login', async () => {
    tokenStore.set('some-token')
    const api = mockApi({ 'POST /auth/login': () => problem(401, 'Invalid email or password.') })

    await expect(apiPost('/auth/login', { email: 'a', password: 'b' })).rejects.toMatchObject({
      status: 401,
      message: 'Invalid email or password.',
    })
    expect(api.calls('POST /auth/refresh')).toHaveLength(0)
  })

  it('exposes Retry-After and field errors from problem responses', async () => {
    mockApi({
      'POST /auth/login': () => problem(429, 'Too many', {}, { 'Retry-After': '120' }),
      'POST /auth/register': () => problem(400, 'Invalid', { errors: [{ field: 'email', message: 'bad email' }] }),
    })

    const limited = await apiPost('/auth/login', {}).catch((error: ApiError) => error)
    const invalid = await apiPost('/auth/register', {}).catch((error: ApiError) => error)

    expect(limited).toBeInstanceOf(ApiError)
    expect((limited as ApiError).retryAfter).toBe(120)
    expect((invalid as ApiError).fieldError('email')).toBe('bad email')
  })

  it('clears the token on logout even if the server fails', async () => {
    tokenStore.set('token-1')
    mockApi({ 'POST /auth/logout': () => problem(500, 'down') })

    await expect(logoutSession()).rejects.toBeInstanceOf(ApiError)
    expect(tokenStore.get()).toBeNull()
  })
})
