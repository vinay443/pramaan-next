# contracts/mock-data

**One** prototype fixture set, shared by all three stacks. Behaviour is modelled
on the existing ECS system (evidence lifecycle, scheduler runs, deterministic
PASS/WARNING/FAIL validation).

| File | Contents |
|---|---|
| `manifest.json` | index + `referenceNow` + freshness rules |
| `applications.json` | Net Banking, Mobile Banking, Payments + environments |
| `agents.json` | agents + assets they collect from |
| `frameworks.json` | frameworks + controls |
| `evidence.json` | evidence records (`EvidenceView` shape) |
| `scheduler-runs.json` | scheduler runs (`RunView`) + source list |
| `results.json` | per-check PASS / WARNING / FAIL / NOT_APPLICABLE outcomes |

Shapes and cross-references: see [`SCHEMA.md`](./SCHEMA.md). Types line up with
[`../openapi/pramaan-backend.yaml`](../openapi/pramaan-backend.yaml).

## Rules

- **Do not copy this data into component/page/service code.** Load it from here.
- Keep every file small. Add rows only when a scenario needs one.
- Change a shape here first, then update the consumers.

## How each stack consumes it

### React (`frontend-react/`)
`src/api/mocks.ts` imports these JSON files (alias `@mockdata`) and adapts them to
the API response shapes. No dataset literals live in pages/components.

### Go (`agents-go/`)
Load as test fixtures with `os.ReadFile("../../contracts/mock-data/<file>.json")`
or `//go:embed`. Use `agents.json` / `applications.json` to drive simulation
targets; assert collector output against `results.json`.

### Java (`backend-java/`)
Read from the classpath/test resources or a relative path in an integration-test
profile seeder, e.g. a `MockDataLoader` that parses `applications.json` /
`frameworks.json` / `evidence.json` and inserts rows. Keep it test/demo-scoped —
production seeding stays in Flyway.
