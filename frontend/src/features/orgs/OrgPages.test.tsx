import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../../api/tokenStore'
import { routes } from '../../app/router'
import { json, mockApi, problem } from '../../test/mockApi'
import { AuthProvider } from '../auth/AuthProvider'
import type { Member, Organization, OrgRole } from './orgApi'

const ME = { id: 'u-me', email: 'me@example.com', displayName: 'Soufiane' }
const SIGNED_IN = {
  'POST /auth/refresh': () => json({ accessToken: 'token', expiresIn: 900, tokenType: 'Bearer' }),
  'GET /me': () => json(ME),
  'GET /meta': () => json({ name: 'flowpilot', version: '0.1.0', instance: 'test' }),
}

function org(role: OrgRole, overrides: Partial<Organization> = {}): Organization {
  return { id: 'o1', name: 'Acme', slug: 'acme', timeZone: 'UTC', role, createdAt: '2026-11-01T00:00:00Z', version: 0, ...overrides }
}

function member(userId: string, displayName: string, role: OrgRole): Member {
  return { userId, displayName, email: `${userId}@example.com`, role, joinedAt: '2026-11-01T00:00:00Z', version: 3 }
}

function page<T>(items: T[]) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 }
}

/** The backend as seen by a user with `role` in org o1, with three members. */
function orgBackend(role: OrgRole, extra: Record<string, (init: RequestInit) => Response> = {}) {
  const current = org(role)
  return mockApi({
    ...SIGNED_IN,
    'GET /orgs': () => json([current]),
    'GET /orgs/o1': () => json(current),
    'GET /orgs/o1/members?page=0&size=20': () =>
      json(page([member('u-owner', 'Olivia', 'OWNER'), member(ME.id, ME.displayName, role), member('u-carl', 'Carl', 'MEMBER')])),
    'GET /orgs/o1/invitations': () => json([]),
    ...extra,
  })
}

function renderApp(path: string) {
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

function body(init: RequestInit) {
  return JSON.parse(init.body as string)
}

describe('organizations', () => {
  beforeEach(() => {
    tokenStore.set(null)
    localStorage.clear()
  })

  it('asks a new user to create their first organization and opens it', async () => {
    const created = org('OWNER', { id: 'o-new', name: 'Pilot Team' })
    let orgs: Organization[] = []
    const api = mockApi({
      ...SIGNED_IN,
      'GET /orgs': () => json(orgs),
      'POST /orgs': () => {
        orgs = [created]
        return json(created, 201)
      },
      'GET /orgs/o-new': () => json(created),
    })
    const router = renderApp('/')
    const user = userEvent.setup()

    expect(await screen.findByRole('heading', { name: 'Welcome, Soufiane' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Organization name'), '  Pilot Team ')
    await user.click(screen.getByRole('button', { name: 'Create organization' }))

    expect(await screen.findByRole('heading', { name: 'Pilot Team' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/orgs/o-new')
    expect(body(api.calls('POST /orgs')[0])).toEqual({ name: 'Pilot Team' })
  })

  it('opens the organization used last time', async () => {
    localStorage.setItem('flowpilot.lastOrgId', 'o2')
    const second = org('MEMBER', { id: 'o2', name: 'Beta Corp' })
    mockApi({ ...SIGNED_IN, 'GET /orgs': () => json([org('OWNER'), second]), 'GET /orgs/o2': () => json(second) })

    const router = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Beta Corp' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/orgs/o2')
  })

  it('shows "not found" for an organization the user is not in', async () => {
    mockApi({ ...SIGNED_IN, 'GET /orgs': () => json([]), 'GET /orgs/nope': () => problem(404, 'Organization not found.') })

    renderApp('/orgs/nope')

    expect(await screen.findByRole('heading', { name: 'Organization not found' })).toBeInTheDocument()
  })

  it('lets an owner invite, change roles and remove members', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const api = orgBackend('OWNER', {
      'POST /orgs/o1/invitations': () =>
        json({ id: 'i1', email: 'new@example.com', role: 'ADMIN', invitedBy: ME.id, createdAt: '', expiresAt: '' }, 201),
      'PATCH /orgs/o1/members/u-carl': () => json(member('u-carl', 'Carl', 'PROJECT_MANAGER')),
      'DELETE /orgs/o1/members/u-carl': () => new Response(null, { status: 204 }),
    })
    renderApp('/orgs/o1/members')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Email'), 'new@example.com')
    await user.selectOptions(screen.getByLabelText('Role'), 'ADMIN')
    await user.click(screen.getByRole('button', { name: 'Send invite' }))
    expect(await screen.findByText('Invitation sent to new@example.com.')).toBeInTheDocument()
    expect(body(api.calls('POST /orgs/o1/invitations')[0])).toEqual({ email: 'new@example.com', role: 'ADMIN' })

    await user.selectOptions(await screen.findByLabelText('Role of Carl'), 'PROJECT_MANAGER')
    await waitFor(() => expect(api.calls('PATCH /orgs/o1/members/u-carl')).toHaveLength(1))
    // The version is sent back, so a concurrent change is detected instead of overwritten.
    expect(body(api.calls('PATCH /orgs/o1/members/u-carl')[0])).toEqual({ role: 'PROJECT_MANAGER', version: 3 })

    await user.click(screen.getByRole('button', { name: 'Remove Carl' }))
    await waitFor(() => expect(api.calls('DELETE /orgs/o1/members/u-carl')).toHaveLength(1))
  })

  it('never offers an admin a way to touch an owner', async () => {
    orgBackend('ADMIN')
    renderApp('/orgs/o1/members')

    const carlsRole = await screen.findByLabelText('Role of Carl')
    expect(within(carlsRole).queryByRole('option', { name: 'Owner' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Role of Olivia')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove Olivia' })).not.toBeInTheDocument()
    expect(within(screen.getByLabelText('Role')).queryByRole('option', { name: 'Owner' })).not.toBeInTheDocument()
  })

  it('shows a plain member the list only, and lets them leave', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const api = orgBackend('MEMBER', { 'DELETE /orgs/o1/members/u-me': () => new Response(null, { status: 204 }) })
    const router = renderApp('/orgs/o1/members')
    const user = userEvent.setup()

    expect(await screen.findByText('Carl')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Invite a member' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: /Role of/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Settings' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Leave' }))

    await waitFor(() => expect(router.state.location.pathname).not.toBe('/orgs/o1/members'))
    expect(api.calls('DELETE /orgs/o1/members/u-me')).toHaveLength(1)
  })

  it('shows the backend message when the last owner tries to leave', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    orgBackend('OWNER', {
      'GET /orgs/o1/members?page=0&size=20': () => json(page([member(ME.id, ME.displayName, 'OWNER')])),
      'DELETE /orgs/o1/members/u-me': () => problem(409, 'An organization must keep at least one owner.'),
    })
    renderApp('/orgs/o1/members')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Leave' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('An organization must keep at least one owner.')
  })

  it('saves settings with the version and explains a concurrent change', async () => {
    let saves = 0
    const api = orgBackend('ADMIN', {
      'PATCH /orgs/o1': () =>
        ++saves === 1 ? json(org('ADMIN', { name: 'Acme Labs', version: 1 })) : problem(409, 'Changed by someone else.'),
    })
    renderApp('/orgs/o1/settings')
    const user = userEvent.setup()

    const name = await screen.findByLabelText('Name')
    await user.clear(name)
    await user.type(name, 'Acme Labs')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Settings saved.')).toBeInTheDocument()
    expect(body(api.calls('PATCH /orgs/o1')[0])).toEqual({ name: 'Acme Labs', timeZone: 'UTC', version: 0 })

    await user.type(name, ' 2')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed these settings just now.')
  })

  it('joins an organization from the invitation link only after a click', async () => {
    const joined = org('MEMBER', { id: 'o9', name: 'Invited Org' })
    const api = mockApi({
      ...SIGNED_IN,
      'GET /orgs': () => json([joined]),
      'POST /invitations/accept': () => json(joined),
      'GET /orgs/o9': () => json(joined),
    })
    const router = renderApp('/invitations/accept?token=secret-token')
    const user = userEvent.setup()

    expect(await screen.findByRole('heading', { name: 'Join an organization' })).toBeInTheDocument()
    // The page says which account will join (the header shows it too).
    expect(within(screen.getByRole('main')).getByText('me@example.com')).toBeInTheDocument()
    expect(api.calls('POST /invitations/accept')).toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Accept invitation' }))

    expect(await screen.findByRole('heading', { name: 'Invited Org' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/orgs/o9')
    expect(body(api.calls('POST /invitations/accept')[0])).toEqual({ token: 'secret-token' })
  })

  it('brings a signed-out invitee back to the invitation after signing in', async () => {
    mockApi({ 'POST /auth/refresh': () => problem(401, 'Your session has expired.') })

    const router = renderApp('/invitations/accept?token=secret-token')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.state).toEqual({
      from: { pathname: '/invitations/accept', search: '?token=secret-token' },
    })
  })

  it('explains an invitation that cannot be accepted', async () => {
    mockApi({
      ...SIGNED_IN,
      'GET /orgs': () => json([]),
      'POST /invitations/accept': () => problem(404, 'This invitation is invalid or has expired.'),
    })
    renderApp('/invitations/accept?token=old')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Accept invitation' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('This invitation is invalid or has expired.')
  })
})
