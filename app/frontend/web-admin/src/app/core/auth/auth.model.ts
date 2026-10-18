/** POST /auth/login, /auth/microsoft and /auth/refresh response, per docs/api/auth.openapi.yaml TokenResponse. */
export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  /** Null only for a visitor pass, which the portal never holds. */
  refreshToken: string | null;
  refreshExpiresIn: number | null;
  userId: string;
  roles: Role[];
}

export type Role =
  | 'ROLE_GUEST'
  | 'ROLE_STUDENT'
  | 'ROLE_PROFESSOR'
  | 'ROLE_STAFF'
  | 'ROLE_ADMIN'
  | 'ROLE_RECEPTION'
  | 'ROLE_MAINTENANCE'
  | 'ROLE_MODERATION'
  | 'ROLE_WELLBEING';

export const ALL_ROLES: Role[] = [
  'ROLE_GUEST',
  'ROLE_STUDENT',
  'ROLE_PROFESSOR',
  'ROLE_STAFF',
  'ROLE_ADMIN',
  'ROLE_RECEPTION',
  'ROLE_MAINTENANCE',
  'ROLE_MODERATION',
  'ROLE_WELLBEING',
];

export function roleLabel(role: string): string {
  return role.replace(/^ROLE_/, '');
}
