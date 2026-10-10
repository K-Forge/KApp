/**
 * Mirrors Role in docs/api/user.openapi.yaml. An account holds one profile role - student,
 * professor or staff - and any number of permissions.
 */
export type DirectoryRole =
  | 'ROLE_STUDENT'
  | 'ROLE_PROFESSOR'
  | 'ROLE_STAFF'
  | 'ROLE_ADMIN'
  | 'ROLE_RECEPTION'
  | 'ROLE_MAINTENANCE'
  | 'ROLE_MODERATION'
  | 'ROLE_WELLBEING';

export const DIRECTORY_ROLES: DirectoryRole[] = [
  'ROLE_STUDENT',
  'ROLE_PROFESSOR',
  'ROLE_STAFF',
  'ROLE_ADMIN',
  'ROLE_RECEPTION',
  'ROLE_MAINTENANCE',
  'ROLE_MODERATION',
  'ROLE_WELLBEING',
];

/**
 * Mirrors DirectoryEntry in docs/api/user.openapi.yaml: an account as the directory shows it, with
 * nothing academic. An administrator manages accounts, not records.
 */
export interface DirectoryEntry {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  avatarUrl: string | null;
  roles: DirectoryRole[];
  active: boolean;
}

export interface UserListFilters {
  page: number;
  size: number;
  role?: DirectoryRole;
  active?: boolean;
  q?: string;
}
