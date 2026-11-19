import { TestBed } from '@angular/core/testing';
import { I18nService } from '../../../core/i18n/i18n.service';
import { RoleBadgeComponent } from './role-badge.component';

describe('RoleBadgeComponent', () => {
  let i18n: I18nService;

  beforeEach(() => {
    i18n = TestBed.inject(I18nService);
  });

  afterEach(() => i18n.set('system'));

  function badge(role: string): HTMLElement {
    const fixture = TestBed.createComponent(RoleBadgeComponent);
    fixture.componentRef.setInput('role', role);
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('.badge');
  }

  it('reads "Sin rol" for an account the directory does not classify, never a role it does not hold', () => {
    i18n.set('es');
    expect(badge('ROLE_UNASSIGNED').textContent?.trim()).toBe('Sin rol');
    i18n.set('en');
    expect(badge('ROLE_UNASSIGNED').textContent?.trim()).toBe('No role');
    expect(badge('ROLE_UNASSIGNED').classList).toContain('badge-warning');
  });

  it('names every other role as before', () => {
    expect(badge('ROLE_STAFF').textContent?.trim()).toBe('STAFF');
    expect(badge('ROLE_STUDENT').classList).toContain('badge-success');
  });
});
