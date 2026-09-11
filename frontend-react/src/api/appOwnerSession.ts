// Identity for App-Owner-scoped API calls. There is no real login anywhere in this
// app (see PersonaLogin) — the backend's own identity model for this role is the
// same demo-persona simulation used by evidence-approval RBAC (X-User-Role /
// X-User-Username headers), extended with a username so application ownership can
// be enforced server-side. The persona selector only ever offers "App Owner" as a
// single role, so a single fixed demo username is enough to exercise real scoping.

export const APP_OWNER_ROLE = 'APP_OWNER'
export const APP_OWNER_USERNAME = 'app.owner.demo'

export function appOwnerHeaders(): Record<string, string> {
  return { 'X-User-Role': APP_OWNER_ROLE, 'X-User-Username': APP_OWNER_USERNAME }
}
