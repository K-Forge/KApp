import { Injectable, computed, signal } from '@angular/core';
import { decodeJwt, isTokenExpired, rolesOf, secondsUntilExpiry } from './jwt.util';
import type { DecodedToken } from './jwt.model';

const STORAGE_KEY = 'kapp-admin:token';

/**
 * The one place the access token lives. Everything that needs it - the auth interceptor, the
 * identity screen, the role inspector, the 401 handler - reads this instead of touching
 * localStorage directly, so there is exactly one definition of "logged in" and one place that
 * clears the session.
 */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  private readonly rawSignal = signal<string | null>(readStoredToken());

  readonly raw = this.rawSignal.asReadonly();

  readonly decoded = computed<DecodedToken | null>(() => {
    const token = this.rawSignal();
    return token ? decodeJwt(token) : null;
  });

  readonly isAuthenticated = computed(() => this.rawSignal() !== null);

  /**
   * Whether the token had expired when it was stored or last changed. It does not tick: a
   * computed re-runs only when the token does. Code that must notice the deadline pass reads
   * `isTokenExpired` against a clock instead.
   */
  readonly isExpired = computed(() => {
    const decoded = this.decoded();
    return decoded ? isTokenExpired(decoded.claims) : true;
  });

  readonly roles = computed(() => {
    const decoded = this.decoded();
    return decoded ? rolesOf(decoded.claims) : [];
  });

  readonly secondsUntilExpiry = computed(() => {
    const decoded = this.decoded();
    return decoded ? secondsUntilExpiry(decoded.claims) : 0;
  });

  set(token: string): void {
    this.rawSignal.set(token);
    try {
      localStorage.setItem(STORAGE_KEY, token);
    } catch {
      // Storage can be unavailable (private browsing with storage disabled); the session then
      // just doesn't survive a reload, which is a graceful degradation rather than a crash.
    }
  }

  clear(): void {
    this.rawSignal.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // See set(): storage access can throw, clearing in-memory state is enough.
    }
  }
}

function readStoredToken(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}
