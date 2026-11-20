/**
 * The permissions auth 1.0 adds on top of a profile role, any one of which opens this portal: the
 * same list `POST /auth/microsoft` checks for the `PORTAL` client, and KappRoles' permissions. A
 * profile role alone - student, professor, staff - is the apps', not the portal's.
 */
export const PORTAL_PERMISSIONS = ['ROLE_ADMIN', 'ROLE_RECEPTION', 'ROLE_MAINTENANCE', 'ROLE_MODERATION', 'ROLE_WELLBEING'];

/** Whether an account holding `roles` may sign in to the portal at all. */
export function opensPortal(roles: readonly string[]): boolean {
  return roles.some((role) => PORTAL_PERMISSIONS.includes(role));
}

/**
 * Whether an account holding `roles` may open a section that needs any one of `needs`. No `needs`
 * means every account the portal lets in. This decides what the portal shows; the API's own
 * `x-roles` decide what anybody can do, whatever it shows.
 */
export function mayOpen(roles: readonly string[], needs: readonly string[] | undefined): boolean {
  return !needs || needs.some((role) => roles.includes(role));
}
