/** POST /auth/login response, per docs/api/auth.openapi.yaml TokenResponse. */
export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  userId: string;
  roles: Role[];
}

export type Role = 'ROLE_GUEST' | 'ROLE_STUDENT' | 'ROLE_PROFESSOR' | 'ROLE_STAFF' | 'ROLE_ADMIN';

export const ALL_ROLES: Role[] = ['ROLE_GUEST', 'ROLE_STUDENT', 'ROLE_PROFESSOR', 'ROLE_STAFF', 'ROLE_ADMIN'];

export function roleLabel(role: string): string {
  return role.replace(/^ROLE_/, '');
}
