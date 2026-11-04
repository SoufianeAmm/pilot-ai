# FlowPilot AI: frontend

React + TypeScript + Vite + Tailwind CSS, with TanStack Query for server state and React Router for routing.

```powershell
npm install
npm run dev        # http://localhost:5173 (proxies /api to the backend on :8090)
npm run lint
npm test
npm run build
```

Layout: `src/app` (router, providers, layout), `src/api` (HTTP client), `src/features/<feature>` (components, hooks and tests per feature), `src/pages`.

See [../docs/development.md](../docs/development.md) for running the full stack.
