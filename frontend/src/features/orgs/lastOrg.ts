/**
 * Remembers the last organization the user opened, so "/" goes back to it. A convenience only:
 * storage can be unavailable (private mode, blocked site data), and then the first org is used.
 */
const KEY = 'flowpilot.lastOrgId'

export const lastOrg = {
  get(): string | null {
    try {
      return window.localStorage.getItem(KEY)
    } catch {
      return null
    }
  },

  set(orgId: string): void {
    try {
      window.localStorage.setItem(KEY, orgId)
    } catch {
      // Not remembering is fine.
    }
  },

  clear(): void {
    try {
      window.localStorage.removeItem(KEY)
    } catch {
      // Nothing to clear.
    }
  },
}
