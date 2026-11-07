import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { tokenStore } from '../../api/tokenStore'
import { routes } from '../../app/router'
import { json, mockApi, problem } from '../../test/mockApi'
import { AuthProvider } from './AuthProvider'

const USER = { id: 'u1', email: 'soufiane@example.com', displayName: 'Soufiane' }
const TOKENS = { accessToken: 'access-1', expiresIn: 900, tokenType: 'Bearer' }
// A new user has no organization yet, so "/" shows the onboarding page ("Welcome, <name>").
const META = { 'GET /orgs': () => json([]) }
const NO_SESSION = { 'POST /auth/refresh': () => problem(401, 'Your session has expired.') }

function renderApp(path = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
  return router
}

describe('authentication flow', () => {
  beforeEach(() => {
    tokenStore.set(null)
  })

  it('sends a signed-out visitor to the login page', async () => {
    mockApi(NO_SESSION)

    const router = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('keeps the user signed in across a reload through the refresh cookie', async () => {
    mockApi({ ...META, 'POST /auth/refresh': () => json(TOKENS), 'GET /me': () => json(USER) })

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Welcome, Soufiane' })).toBeInTheDocument()
  })

  it('shows the backend message for wrong credentials', async () => {
    mockApi({ ...NO_SESSION, 'POST /auth/login': () => problem(401, 'Invalid email or password.') })
    renderApp('/login')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Email'), 'soufiane@example.com')
    await user.type(screen.getByLabelText('Password'), 'wrong password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
    expect(screen.getByLabelText('Password')).toHaveValue('')
  })

  it('explains the lockout when login is rate limited', async () => {
    mockApi({ ...NO_SESSION, 'POST /auth/login': () => problem(429, 'Too many', {}, { 'Retry-After': '600' }) })
    renderApp('/login')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Email'), 'soufiane@example.com')
    await user.type(screen.getByLabelText('Password'), 'whatever')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Try again in 10 minutes.')
  })

  it('signs in and returns to the page that required it', async () => {
    mockApi({
      ...META,
      ...NO_SESSION,
      'POST /auth/login': () => json(TOKENS),
      'GET /me': () => json(USER),
    })
    const router = renderApp('/?welcome=1')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Email'), 'soufiane@example.com')
    await user.type(screen.getByLabelText('Password'), 'correct horse battery')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Welcome, Soufiane' })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?welcome=1')
    expect(tokenStore.get()).toBe('access-1')
  })

  it('validates the registration form before calling the backend', async () => {
    const api = mockApi(NO_SESSION)
    renderApp('/register')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Name'), 'Soufiane')
    await user.type(screen.getByLabelText('Email'), 'soufiane@example.com')
    await user.type(screen.getByLabelText('Password'), 'short')
    await user.type(screen.getByLabelText('Confirm password'), 'short')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Use at least 12 characters.')
    expect(api.calls('POST /auth/register')).toHaveLength(0)
  })

  it('shows an already-registered email next to the email field', async () => {
    mockApi({ ...NO_SESSION, 'POST /auth/register': () => problem(409, 'An account with this email already exists.') })
    renderApp('/register')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Name'), 'Soufiane')
    await user.type(screen.getByLabelText('Email'), 'soufiane@example.com')
    await user.type(screen.getByLabelText('Password'), 'correct horse battery')
    await user.type(screen.getByLabelText('Confirm password'), 'correct horse battery')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('An account with this email already exists.')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
  })

  it('registers, signs in automatically and signs out again', async () => {
    const api = mockApi({
      ...META,
      ...NO_SESSION,
      'POST /auth/register': () => json(USER, 201),
      'POST /auth/login': () => json(TOKENS),
      'GET /me': () => json(USER),
      'POST /auth/logout': () => new Response(null, { status: 204 }),
    })
    const router = renderApp('/register')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Name'), 'Soufiane')
    await user.type(screen.getByLabelText('Email'), 'soufiane@example.com')
    await user.type(screen.getByLabelText('Password'), 'correct horse battery')
    await user.type(screen.getByLabelText('Confirm password'), 'correct horse battery')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('heading', { name: 'Welcome, Soufiane' })).toBeInTheDocument()
    expect(JSON.parse(api.calls('POST /auth/register')[0].body as string)).toEqual({
      displayName: 'Soufiane',
      email: 'soufiane@example.com',
      password: 'correct horse battery',
    })

    await user.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(tokenStore.get()).toBeNull()
  })

  it('returns to the login page when the session can no longer be refreshed', async () => {
    let refreshes = 0
    mockApi({
      'POST /auth/refresh': () => (++refreshes === 1 ? json(TOKENS) : problem(401, 'Your session has expired.')),
      'GET /me': () => json(USER),
      'GET /orgs': () => problem(401, 'expired'),
    })

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})
