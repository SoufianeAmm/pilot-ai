import { SystemStatus } from '../features/system/SystemStatus'

export function HomePage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Welcome to FlowPilot AI</h1>
        <p className="mt-1 text-slate-600">
          Project operations with an AI analyst that answers from your real project data.
        </p>
      </div>
      <SystemStatus />
    </div>
  )
}
