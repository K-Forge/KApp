import { Injectable, computed, signal } from '@angular/core';
import { decodeJwt, isTokenExpired, rolesOf, secondsUntilExpiry } from './jwt.util';
import type { DecodedToken } from './jwt.model';

const STORAGE_KEY = 'kapp-admin:token';
const REFRESH_KEY = 'kapp-admin:refresh-token';

/**
 * The one place the access token lives. Everything that needs it - the auth interceptor, the
 * identity screen, the role inspector, the 401 handler - reads this instead of touching
 * localStorage directly, so there is exactly one definition of "logged in" and one place that
 * clears the session.
 */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  private readonly rawSignal = signal<string | null>(readStored(STORAGE_KEY));
  private readonly refreshSignal = signal<string | null>(readStored(REFRESH_KEY));

  readonly raw = this.rawSignal.asReadonly();

  /** What renews the session at `POST /auth/refresh`; null when nothing can, as with a pasted token. */
  readonly refreshToken = this.refreshSignal.asReadonly();

  constructor() {
    // Every tab of the portal shares one session. When another tab renews or ends it, this one
    // takes the new tokens instead of renewing later with a refresh token already used, which the
    // server reads as a stolen copy and answers by revoking the session in every tab.
    try {
      window.addEventListener('storage', (event) => {
        if (event.key === null || event.key === STORAGE_KEY || event.key === REFRESH_KEY) {
          this.reload();
        }
      });
    } catch {
      // No window to listen to: one tab, nothing to keep in step.
    }
  }

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
    store(STORAGE_KEY, token);
  }

  /**
   * A session the server opened or renewed: its access token and the refresh token that comes
   * with it. Renewal rotates the refresh token, so the old one is replaced, never kept.
   */
  start(tokens: { accessToken: string; refreshToken?: string | null }): void {
    this.set(tokens.accessToken);
    this.refreshSignal.set(tokens.refreshToken ?? null);
    store(REFRESH_KEY, tokens.refreshToken ?? null);
  }

  /** Reads both tokens again from storage, where another tab may have renewed or ended the session. */
  reload(): void {
    this.rawSignal.set(readStored(STORAGE_KEY));
    this.refreshSignal.set(readStored(REFRESH_KEY));
  }

  clear(): void {
    this.rawSignal.set(null);
    this.refreshSignal.set(null);
    store(STORAGE_KEY, null);
    store(REFRESH_KEY, null);
  }
}

function readStored(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function store(key: string, value: string | null): void {
  try {
    if (value === null) {
      localStorage.removeItem(key);
    } else {
      localStorage.setItem(key, value);
    }
  } catch {
    // Storage can be unavailable (private browsing with storage disabled); the session then
    // just doesn't survive a reload, which is a graceful degradation rather than a crash.
  }
}
