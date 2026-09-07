# contracts/

Single source of truth for cross-service interfaces shared by `backend-java`,
`agents-go`, and `frontend-react`.

- `openapi/` — REST API definitions (OpenAPI 3.1 YAML).
- `proto/` — gRPC / protobuf definitions for backend ⇄ agents communication.

No contracts are defined yet. Add them here before implementing the services that
produce or consume them, and generate client/server stubs from these files rather
than hand-writing them.
