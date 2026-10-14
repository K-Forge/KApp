import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, firstValueFrom, tap, throwError } from 'rxjs';
import { ApiConfigService } from '../config/api-config.service';
import { parseApiError } from '../http/api-error.util';
import { AppHttpError } from '../http/api-http-error';
import type { TokenResponse } from './auth.model';
import { TokenStore } from './token.store';

/**
 * Deliberately bypasses ApiClientService: login is the one call the app makes with no token to
 * attach, and it is also where a bad base URL first becomes visible, so its errors go through
 * the same AppHttpError/ApiError path as everything else rather than a special case.
 */
/** Who may sign in to this portal. Later, some administrative staff too. */
export const PORTAL_ROLES = ['ROLE_ADMIN'];

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApiConfigService);
  private readonly tokenStore = inject(TokenStore);
  private readonly router = inject(Router);
  private renewal: Promise<boolean> | null = null;

  login(email: string, password: string): Observable<TokenResponse> {
    // The portal is for administrators: the server refuses any other account here, before it
    // offers to replace a temporary password - a student's is replaced in the app, not here.
    return this.http.post<TokenResponse>(`${this.config.baseUrl()}/auth/login`, { email, password, allowedRoles: PORTAL_ROLES }).pipe(
      tap((response) => this.tokenStore.start(response)),
      catchError((err) => throwError(() => new AppHttpError(parseApiError(err)))),
    );
  }

  /**
   * A temporary password an administrator issued, swapped for the person's own: the server
   * answers as login does, so the session starts with the new password.
   */
  changePassword(email: string, currentPassword: string, newPassword: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${this.config.baseUrl()}/auth/password`, { email, currentPassword, newPassword, allowedRoles: PORTAL_ROLES })
      .pipe(
      tap((response) => this.tokenStore.start(response)),
      catchError((err) => throwError(() => new AppHttpError(parseApiError(err)))),
    );
  }

  /**
   * Swaps the refresh token for a new access token and the next refresh token.
   *
   * <p>One renewal at a time, in this tab and across the portal's tabs: the server rotates the
   * refresh token, so a second request with the same one would read as a stolen copy and revoke
   * the whole session. Callers that ask while a renewal is under way get that one's answer; a tab
   * that waited while another renewed takes that tab's tokens instead of asking again.
   *
   * @returns whether the session goes on; false when there is nothing to renew with or the
   *          server refused (signed out elsewhere, deactivated, or past its 12 hours)
   */
  renew(): Promise<boolean> {
    if (!this.renewal) {
      const held = this.tokenStore.refreshToken();
      this.renewal = withRenewalLock(() => this.refresh(held)).finally(() => (this.renewal = null));
    }
    return this.renewal;
  }

  private async refresh(held: string | null): Promise<boolean> {
    this.tokenStore.reload();
    const refreshToken = this.tokenStore.refreshToken();
    if (!refreshToken) {
      return false;
    }
    if (refreshToken !== held) {
      // Another tab renewed while this one waited: its tokens are this tab's now.
      return true;
    }
    try {
      const response = await firstValueFrom(
        this.http.post<TokenResponse>(`${this.config.baseUrl()}/auth/refresh`, { refreshToken }),
      );
      this.tokenStore.start(response);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Clears the session and leaves the portal.
   *
   * <p>The fallback is not defensive padding. `/login` is a lazily loaded route, and its chunk
   * is named by content hash - so after a deploy, a tab that has been open since before it
   * cannot fetch that chunk any more. The router then cancels the navigation and resolves
   * `false`, which left the token cleared and the person still sitting inside the portal: no
   * session, every call failing, and no way out but a manual reload. A full document navigation
   * both gets them to the sign-in page and fetches the build that actually exists.
   */
  logout(): void {
    this.tokenStore.clear();
    this.leaveFor({});
  }

  /**
   * Ends a session whose token has run out, the moment it does, and says so on the sign-in
   * page - which then brings the person back to the page they were on.
   *
   * <p>Waiting for the next call to come back `401` left the portal showing everything the dead
   * token had read, for as long as nobody clicked anything: a list of users on an unattended
   * screen, with a red "expired" badge over it.
   */
  expire(): void {
    const returnUrl = this.router.url;
    this.tokenStore.clear();
    this.leaveFor(returnUrl.startsWith('/login') ? { reason: 'expired' } : { reason: 'expired', returnUrl });
  }

  private leaveFor(queryParams: Record<string, string>): void {
    this.router
      .navigate(['/login'], { queryParams })
      .then((navigated) => {
        if (!navigated) {
          this.hardRedirectToLogin(queryParams);
        }
      })
      .catch(() => this.hardRedirectToLogin(queryParams));
  }

  private hardRedirectToLogin(queryParams: Record<string, string>): void {
    const query = new URLSearchParams(queryParams).toString();
    window.location.assign(query ? `/login?${query}` : '/login');
  }
}

/**
 * Runs a renewal holding the portal's one renewal lock, so two tabs never send the same refresh
 * token. Browsers without the Web Locks API run it as is; the storage event still keeps the tabs
 * in step there.
 */
function withRenewalLock<T>(work: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === 'undefined' ? undefined : navigator.locks;
  return locks ? locks.request('kapp-admin:renewal', work) : work();
}
