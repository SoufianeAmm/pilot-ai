import type { Location } from 'react-router'

export interface RedirectState {
  from?: Pick<Location, 'pathname' | 'search'>
}

/** Where to go after signing in: the page that sent the user to /login, or home. */
export function redirectTarget(locationState: unknown): string {
  const from = (locationState as RedirectState | null)?.from
  // Only in-app paths: "//evil.example" would be read as another site.
  if (from?.pathname?.startsWith('/') && !from.pathname.startsWith('//')) {
    return from.pathname + (from.search ?? '')
  }
  return '/'
}
