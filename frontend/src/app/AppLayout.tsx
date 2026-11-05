import { useState } from 'react'
import { Link, Outlet } from 'react-router'
import { Button } from '../components/form'
import { useAuth } from '../features/auth/authContext'
import { OrgSwitcher } from '../features/orgs/OrgSwitcher'

export function AppLayout() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="text-lg font-semibold tracking-tight">
            FlowPilot <span className="text-indigo-600">AI</span>
          </Link>
          <AccountMenu />
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}

function AccountMenu() {
  const { state, logout } = useAuth()
  const [signingOut, setSigningOut] = useState(false)

  if (state.status !== 'authenticated') {
    return null
  }

  async function handleLogout() {
    setSigningOut(true)
    await logout()
    setSigningOut(false)
  }

  return (
    <div className="flex min-w-0 items-center gap-3">
      <OrgSwitcher />
      <div className="hidden min-w-0 text-right sm:block">
        <p className="truncate text-sm font-medium">{state.user.displayName}</p>
        <p className="truncate text-xs text-slate-500">{state.user.email}</p>
      </div>
      <Button variant="secondary" onClick={handleLogout} disabled={signingOut}>
        {signingOut ? 'Signing out…' : 'Sign out'}
      </Button>
    </div>
  )
}
