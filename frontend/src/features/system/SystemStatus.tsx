import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { apiGet } from '../../api/client'

export interface AppMeta {
  name: string
  version: string
  instance: string
}

export function SystemStatus() {
  const meta = useQuery({
    queryKey: ['meta'],
    queryFn: ({ signal }) => apiGet<AppMeta>('/meta', signal),
  })

  if (meta.isPending) {
    return <StatusCard tone="neutral" title="Connecting to the backend…" />
  }
  if (meta.isError) {
    return (
      <StatusCard tone="error" title="Backend unreachable">
        Start it with <code className="font-mono">docker compose up -d</code> and reload the page.
      </StatusCard>
    )
  }
  return (
    <StatusCard tone="ok" title="Backend connected">
      Version {meta.data.version} · served by instance <code className="font-mono">{meta.data.instance}</code>
    </StatusCard>
  )
}

const tones = {
  neutral: 'border-slate-200 bg-white',
  ok: 'border-emerald-200 bg-emerald-50',
  error: 'border-rose-200 bg-rose-50',
} as const

function StatusCard({ tone, title, children }: {
  tone: keyof typeof tones
  title: string
  children?: ReactNode
}) {
  return (
    <section role="status" className={`rounded-lg border p-4 ${tones[tone]}`}>
      <h2 className="font-medium">{title}</h2>
      {children && <p className="mt-1 text-sm text-slate-600">{children}</p>}
    </section>
  )
}
