import { describe, expect, it } from 'vitest';
import { navFor, sectionRoles } from './nav';

const paths = (roles: string[]) => navFor(roles).flatMap((group) => group.links.map((link) => link.path));

describe('the sidebar by permission', () => {
  it('shows an administrator every section', () => {
    expect(paths(['ROLE_STAFF', 'ROLE_ADMIN'])).toEqual(expect.arrayContaining(['/data/users', '/data/floors', '/data/visitor-passes', '/my-token']));
  });

  // Reception issues visitor passes, the one thing auth 1.0 lets it do besides an account's own.
  it('shows reception its visitor passes and what every account sees, and nothing of the administration', () => {
    expect(paths(['ROLE_STAFF', 'ROLE_RECEPTION'])).toEqual(['/data/visitor-passes', '/my-token', '/who-can-do-what', '/api-console']);
  });

  it('drops a group with nothing left in it', () => {
    const titles = navFor(['ROLE_STAFF', 'ROLE_MAINTENANCE']).map((group) => group.title);
    expect(titles).toEqual(['Inspect']);
  });

  it('keeps a page with no sidebar entry to administrators, and a sub-page to its section', () => {
    expect(sectionRoles('/data/blocks/A')).toEqual(['ROLE_ADMIN']);
    expect(sectionRoles('/data/floors/EC/P1')).toEqual(['ROLE_ADMIN']);
    expect(sectionRoles('/data/visitor-passes?status=active')).toEqual(['ROLE_ADMIN', 'ROLE_RECEPTION']);
    expect(sectionRoles('/my-token')).toBeUndefined();
  });
});
