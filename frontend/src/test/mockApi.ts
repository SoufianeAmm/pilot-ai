import { vi } from 'vitest'

type Handler = (init: RequestInit) => Response | Promise<Response>

export function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json', ...headers },
  })
}

export function problem(status: number, detail: string, extra: Record<string, unknown> = {}, headers = {}): Response {
  return json({ status, title: 'Error', detail, ...extra }, status, headers)
}

/**
 * Replaces fetch with a fake backend. Keys are "METHOD /path" (without /api/v1). An unknown
 * route fails the test loudly instead of hanging.
 */
export function mockApi(handlers: Record<string, Handler>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const path = String(input).replace(/^\/api\/v1/, '')
    const key = `${init.method ?? 'GET'} ${path}`
    const handler = handlers[key]
    if (!handler) {
      throw new Error(`Unexpected request: ${key}`)
    }
    return handler(init)
  })
  vi.stubGlobal('fetch', fetchMock)
  return {
    fetchMock,
    calls(key: string): RequestInit[] {
      return fetchMock.mock.calls
        .filter(([input, init]) => `${init?.method ?? 'GET'} ${String(input).replace(/^\/api\/v1/, '')}` === key)
        .map(([, init]) => init ?? {})
    },
  }
}

export function header(init: RequestInit, name: string): string | undefined {
  return (init.headers as Record<string, string> | undefined)?.[name]
}
