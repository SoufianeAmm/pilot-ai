import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { logoutSession, refreshSession } from '../../api/client'
import { tokenStore } from '../../api/tokenStore'
import { fetchMe, loginRequest, registerRequest, type RegisterInput } from './authApi'
import { AuthContext, type AuthContextValue, type AuthState } from './authContext'

/**
 * Owns the session. On start it tries the refresh cookie, so a page reload keeps the user signed
 * in; when the API client reports that the session could not be refreshed, it signs the user out.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  const signedOut = useCallback(() => {
    tokenStore.set(null)
    queryClient.clear()
    setState({ status: 'anonymous' })
  }, [queryClient])

  useEffect(() => {
    let active = true
    refreshSession()
      .then(fetchMe)
      .then((user) => active && setState({ status: 'authenticated', user }))
      .catch(() => active && setState({ status: 'anonymous' }))
    return () => {
      active = false
    }
  }, [])

  useEffect(() => tokenStore.onExpired(signedOut), [signedOut])

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await loginRequest(email, password)
    tokenStore.set(tokens.accessToken)
    const user = await fetchMe()
    // Another user's cached data must never show up after switching accounts.
    queryClient.clear()
    setState({ status: 'authenticated', user })
  }, [queryClient])

  const register = useCallback(async (input: RegisterInput) => {
    await registerRequest(input)
    await login(input.email, input.password)
  }, [login])

  const logout = useCallback(async () => {
    try {
      await logoutSession()
    } catch {
      // The local session ends even if the server cannot be reached.
    }
    signedOut()
  }, [signedOut])

  const value = useMemo<AuthContextValue>(
    () => ({ state, login, register, logout }),
    [state, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
