/**
 * Holds the short-lived access token in memory only. It is never written to localStorage or
 * sessionStorage, so an injected script cannot read a stored token, and a page reload simply
 * gets a new one through the HttpOnly refresh cookie.
 */

type SessionListener = () => void

let accessToken: string | null = null
const expiredListeners = new Set<SessionListener>()

export const tokenStore = {
  get(): string | null {
    return accessToken
  },

  set(token: string | null): void {
    accessToken = token
  },

  /** Called by the API client when the session cannot be refreshed any more. */
  expire(): void {
    accessToken = null
    expiredListeners.forEach((listener) => listener())
  },

  /** @returns an unsubscribe function */
  onExpired(listener: SessionListener): () => void {
    expiredListeners.add(listener)
    return () => {
      expiredListeners.delete(listener)
    }
  },
}
