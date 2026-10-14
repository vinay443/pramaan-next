# Evidence metadata edit (UC03-FT006)

`PATCH /api/v1/evidence/{id}/metadata` - body `{"changes":{...},"reason":"..."}`. See `contracts/openapi/pramaan-backend.yaml`.

- Editable: `title` (the `sourceTitle` tag), `environment`, `technology`, `evidenceType`, `frameworks`. Anything else is a 400 naming the key.
- Roles: ADMIN and APP (`SchedulerAccessPolicy.METADATA_EDIT_ROLES`). Other roles get 403 + an ACCESS_DENIED security event.
- **APP ownership is not enforced.** There is no user-to-application mapping: `applications.owner` is free text (seeded e.g. `app.owner.payments`) and the persona name sent as `X-Pramaan-User` is "App Owner". Any APP user may therefore edit evidence of any application. Enforcing it needs a user-to-application table (a deliberate future decision, not a gap in this change).
- State: ARCHIVED -> 409; any other non-DRAFT state returns to DRAFT; DRAFT stays DRAFT. A no-op edit returns 200 with no event.
- Naming: environment/evidenceType changes recompute the name prefix; the NNN is kept if free under the new prefix, otherwise the next one is taken under the per-application naming lock.
- Audit: a `METADATA_EDITED` row in `evidence_lifecycle_event` (no migration; `action` is free text). `note` is JSON: `{"reason":..,"role":..,"changes":{"environment":{"old":..,"new":..},"name":{...}}}`. Read it through `GET /api/v1/evidence/{id}/lifecycle` (`history`).
