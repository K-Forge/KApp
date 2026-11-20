import { describe, expect, it } from 'vitest';
import { mayOpen, opensPortal } from './permissions';

describe('permissions', () => {
  it('opens the portal to any permission and to no profile role alone', () => {
    expect(opensPortal(['ROLE_STAFF', 'ROLE_ADMIN'])).toBe(true);
    expect(opensPortal(['ROLE_PROFESSOR', 'ROLE_WELLBEING'])).toBe(true);
    expect(opensPortal(['ROLE_STUDENT'])).toBe(false);
    expect(opensPortal(['ROLE_STAFF'])).toBe(false);
  });

  it('opens a section that names none to everybody, and one that names some to those who hold one', () => {
    expect(mayOpen(['ROLE_RECEPTION'], undefined)).toBe(true);
    expect(mayOpen(['ROLE_RECEPTION'], ['ROLE_ADMIN', 'ROLE_RECEPTION'])).toBe(true);
    expect(mayOpen(['ROLE_RECEPTION'], ['ROLE_ADMIN'])).toBe(false);
  });
});
