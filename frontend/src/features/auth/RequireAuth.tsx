import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './authContext'
import { redirectTarget, type RedirectState } from './redirect'

/** Pages below this route need a signed-in user; others are sent to /login and brought back afterwards. */
export function RequireAuth() {
  const { state } = useAuth()
  const location = useLocation()

  if (state.status === 'loading') {
    return <SessionLoading />
  }
  if (state.status === 'anonymous') {
    const redirect: RedirectState = { from: { pathname: location.pathname, search: location.search } }
    return <Navigate to="/login" replace state={redirect} />
  }
  return <Outlet />
}

/** Login and register pages: a signed-in user goes straight to where they wanted to be. */
export function GuestOnly() {
  const { state } = useAuth()
  const location = useLocation()

  if (state.status === 'loading') {
    return <SessionLoading />
  }
  if (state.status === 'authenticated') {
    return <Navigate to={redirectTarget(location.state)} replace />
  }
  return <Outlet />
}

function SessionLoading() {
  return (
    <p role="status" className="py-16 text-center text-sm text-slate-500">
      Loading your session…
    </p>
  )
}
