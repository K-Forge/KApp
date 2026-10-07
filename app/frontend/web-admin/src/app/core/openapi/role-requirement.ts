import { ALL_ROLES, type Role } from '../auth/auth.model';
import type { ConsoleOperation } from './console-operation.model';

/** `declared` means the contract said so in `x-roles`; `inferred` means its prose was read. */
export type RoleSource = 'declared' | 'inferred';

export type RoleRequirement =
  | { kind: 'public'; source: RoleSource }
  | { kind: 'roles'; roles: Role[]; source: RoleSource }
  | { kind: 'service-only'; source: RoleSource }
  | { kind: 'unknown'; source: RoleSource };

/**
 * Every operation in the specs now carries `x-roles`, written from the authorization the
 * services actually enforce - the `@PreAuthorize` annotations and, for the map, its security
 * configuration.
 *
 * <p>It used to parse the prose in each operation's `description`, and that was wrong in a way
 * worth recording. The last-resort branch asked only "does this role name appear in the text",
 * which cannot tell a role that is *allowed* from one that is *refused*: `GET /api/users/me`
 * says "ROLE_GUEST is refused with 403", so the page listed ROLE_GUEST among the roles that may
 * call it - confidently backwards, about the one rule that had just been tightened for security.
 * A tool whose only job is to say who can do what must not guess from English.
 *
 * <p>The prose parsing stays as a fallback for an operation somebody adds without `x-roles`,
 * and {@link RoleRequirement} says which of the two produced the answer so the page can show it.
 */
export function resolveRoleRequirement(op: ConsoleOperation): RoleRequirement {
  const declared = (op as { xRoles?: unknown }).xRoles;
  if (Array.isArray(declared)) {
    if (declared.length === 0) {
      return { kind: 'public', source: 'declared' };
    }
    if (declared.includes('SERVICE_ONLY')) {
      return { kind: 'service-only', source: 'declared' };
    }
    const roles = declared.filter((r): r is Role => (ALL_ROLES as readonly string[]).includes(r));
    if (roles.length) {
      return { kind: 'roles', roles, source: 'declared' };
    }
  }

  if (Array.isArray(op.security) && op.security.length === 0) {
    return { kind: 'public', source: 'inferred' };
  }

  // YAML block-literal descriptions soft-wrap prose across lines; flattening whitespace means a
  // sentence isn't accidentally cut off at a line break before its regex can see the whole thing.
  const text = `${op.summary} ${op.description}`.replace(/\s+/g, ' ');

  if (/`?ROLE_ADMIN`?\s*only/i.test(text)) {
    return { kind: 'roles', roles: ['ROLE_ADMIN'], source: 'inferred' };
  }
  if (/any authenticated role except\s*`?ROLE_GUEST`?/i.test(text)) {
    return { kind: 'roles', roles: ['ROLE_STUDENT', 'ROLE_PROFESSOR', 'ROLE_ADMIN'], source: 'inferred' };
  }
  if (/any authenticated role/i.test(text)) {
    return { kind: 'roles', roles: [...ALL_ROLES], source: 'inferred' };
  }

  const listMatch = text.match(/(?:Allowed roles|Access|Roles):\s*([^.]+)/i);
  if (listMatch) {
    const roles = extractRoles(listMatch[1]);
    if (roles.length) {
      return { kind: 'roles', roles, source: 'inferred' };
    }
  }

  const fallback = fallbackByPath(op);
  if (fallback) {
    return fallback;
  }

  const anyRoles = extractRoles(text);
  if (anyRoles.length) {
    return { kind: 'roles', roles: anyRoles, source: 'inferred' };
  }

  return { kind: 'unknown', source: 'inferred' };
}

function extractRoles(text: string): Role[] {
  return ALL_ROLES.filter((role) => text.includes(role));
}

/**
 * Only the semaphore catalog endpoints need this: their per-operation descriptions state none of
 * the "Allowed roles"/"Access"/"Roles" phrasings above, and the authorization rule instead lives
 * once, in the spec's top-of-file table (see docs/api/semaphore.openapi.yaml's Authorization
 * section). Every other service states its roles on every operation.
 */
function fallbackByPath(op: ConsoleOperation): RoleRequirement | null {
  if (op.serviceId === 'semaphore') {
    if (op.path.startsWith('/api/catalog')) {
      return { kind: 'roles', roles: op.method === 'get' ? ['ROLE_STUDENT', 'ROLE_PROFESSOR', 'ROLE_STAFF', 'ROLE_ADMIN'] : ['ROLE_ADMIN'], source: 'inferred' };
    }
    if (op.path.startsWith('/api/semaphore/me')) {
      return { kind: 'roles', roles: ['ROLE_STUDENT'], source: 'inferred' };
    }
  }
  return null;
}
