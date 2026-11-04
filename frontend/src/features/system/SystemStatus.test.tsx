import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SystemStatus } from './SystemStatus'

function renderWithQueryClient() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <SystemStatus />
    </QueryClientProvider>,
  )
}

function jsonResponse(body: unknown, status = 200, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } })
}

describe('SystemStatus', () => {
  it('shows version and instance when the backend responds', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ name: 'flowpilot', version: '0.1.0', instance: 'replica-1' }),
    )
    vi.stubGlobal('fetch', fetchMock)

    renderWithQueryClient()

    expect(await screen.findByText('Backend connected')).toBeInTheDocument()
    expect(screen.getByText('replica-1')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/meta', expect.anything())
  })

  it('shows an error state when the backend returns a problem response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ status: 503, title: 'Service Unavailable' }, 503, 'application/problem+json'),
    ))

    renderWithQueryClient()

    expect(await screen.findByText('Backend unreachable')).toBeInTheDocument()
  })
})
