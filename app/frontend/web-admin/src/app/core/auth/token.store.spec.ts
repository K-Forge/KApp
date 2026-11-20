import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TokenStore } from './token.store';

describe('TokenStore', () => {
  let store: TokenStore;

  // The test environment does not always expose a working localStorage; a map stands in.
  const saved = new Map<string, string>();
  const localStorage = {
    getItem: (key: string) => saved.get(key) ?? null,
    setItem: (key: string, value: string) => void saved.set(key, value),
    removeItem: (key: string) => void saved.delete(key),
  };

  beforeEach(() => {
    saved.clear();
    vi.stubGlobal('localStorage', localStorage);
    store = TestBed.inject(TokenStore);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('keeps the refresh token that comes with a session, across a reload', () => {
    store.start({ accessToken: 'a.b.c', refreshToken: 'rt_1' });

    expect(store.raw()).toBe('a.b.c');
    expect(store.refreshToken()).toBe('rt_1');
    // What a reloaded tab reads.
    expect(new TokenStore().refreshToken()).toBe('rt_1');
  });

  // Renewal rotates it: the one sent stops working, so keeping it would only fail later.
  it('replaces the refresh token when the session is renewed', () => {
    store.start({ accessToken: 'a.b.c', refreshToken: 'rt_1' });
    store.start({ accessToken: 'd.e.f', refreshToken: 'rt_2' });

    expect(store.refreshToken()).toBe('rt_2');
  });

  it('forgets both tokens on clear', () => {
    store.start({ accessToken: 'a.b.c', refreshToken: 'rt_1' });
    store.clear();

    expect(store.raw()).toBeNull();
    expect(store.refreshToken()).toBeNull();
    expect(localStorage.getItem('kapp-admin:refresh-token')).toBeNull();
  });

  // A token pasted into the console has nothing to renew it with.
  it('holds no refresh token for a bare access token', () => {
    store.set('a.b.c');

    expect(store.refreshToken()).toBeNull();
  });

  // Tab A renews and tab B renews later with the refresh token A already used: the server reads
  // that as a stolen copy and signs both out. B takes A's tokens as soon as A stores them.
  it('takes the tokens another tab stored', () => {
    const tabA = store;
    const tabB = new TokenStore();
    tabA.start({ accessToken: 'a.b.c', refreshToken: 'rt_1' });
    tabB.reload();

    tabA.start({ accessToken: 'd.e.f', refreshToken: 'rt_2' });
    window.dispatchEvent(new StorageEvent('storage', { key: 'kapp-admin:refresh-token' }));

    expect(tabB.refreshToken()).toBe('rt_2');
    expect(tabB.raw()).toBe('d.e.f');
  });

  it('signs out with the tab that signed out', () => {
    const tabB = new TokenStore();
    store.start({ accessToken: 'a.b.c', refreshToken: 'rt_1' });
    tabB.reload();

    store.clear();
    window.dispatchEvent(new StorageEvent('storage', { key: 'kapp-admin:token' }));

    expect(tabB.raw()).toBeNull();
    expect(tabB.refreshToken()).toBeNull();
  });
});
