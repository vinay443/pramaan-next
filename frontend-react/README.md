# frontend-react

React 18 + TypeScript + Vite. Phase 1 UI for Pramaan Next. **No backend code here** —
it consumes the API defined in `../contracts/openapi/pramaan-backend.yaml`.

## Pages

| Route | Page | Backend endpoints |
|---|---|---|
| `/` | Dashboard | `/evidence`, `/evidence/query/{freshness,source-breakdown}`, `/scheduler/runs` |
| `/evidence` | Evidence Repository | `GET /evidence` (filters + pagination) |
| `/evidence/:id` | Evidence Detail | `/evidence/{id}`, `/{id}/versions`, `/{id}/verify`, `/insight/evidence/{id}/summary` |
| `/evidence/query` | Evidence Query | `GET /evidence/query/{name}` |
| `/bulk-upload` | Bulk Upload | `POST /evidence/bulk` |
| `/scheduler` | Scheduler | `/scheduler/{sources,runs}`, `POST /scheduler/runs`, `/runs/{id}`, `/runs/{id}/retry` |
| `/control-results` | Control Results | `GET /checks`, `GET /check-results` (filters), `POST /checks/evaluate` |
| `/completeness` | Evidence Completeness (P2) | `GET /insight/completeness` |
| `/compliance` | Compliance Dashboard (P2) | `GET /insight/compliance` |
| `/reuse` | Evidence Reuse (P2) | `GET /insight/reuse/{id}`, `POST /insight/reuse/search` |
| `/nl-query` | Natural-language Queries (P2) | `POST /insight/nl-query` |
| `/applications` | Applications | `/applications` (list / create / update) |
| `/agents` | Agents | `GET /agents` (contract `AgentDescriptorView`) |

## Mock fallback

Every endpoint (`src/api/endpoints.ts`) tries the real backend first. On a
connection failure it falls back to deterministic mock data and a banner shows
**"Backend unavailable — showing mock data."** Set `VITE_USE_MOCKS=true` to force
mocks (demos). Real HTTP errors (4xx/5xx) are surfaced, not hidden.

The mock data itself lives in the **shared** fixtures at
`../contracts/mock-data/` (alias `@mockdata`). `src/api/mocks.ts` only *shapes*
those fixtures into API responses — it contains no dataset literals, and pages /
components never embed their own data.

## Commands

```bash
npm install
npm run dev        # http://localhost:5173  (proxies /api -> localhost:8080)
npm run build      # tsc --noEmit + vite build
npm test           # vitest run
npm run typecheck
```

`npm install` may print an allow-scripts warning for `esbuild`; run
`npm approve-scripts esbuild` once (already recorded in `package.json`).

## Tests

Focused Vitest + Testing Library suite (5 files, 11 tests): mock-data filtering,
endpoint fallback behaviour, and rendering of the Dashboard, Evidence Repository,
and Bulk Upload pages against mock data.
