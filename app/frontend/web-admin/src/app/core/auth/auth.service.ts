import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, tap, throwError } from 'rxjs';
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

  login(email: string, password: string): Observable<TokenResponse> {
    // The portal is for administrators: the server refuses any other account here, before it
    // offers to replace a temporary password - a student's is replaced in the app, not here.
    return this.http.post<TokenResponse>(`${this.config.baseUrl()}/auth/login`, { email, password, allowedRoles: PORTAL_ROLES }).pipe(
      tap((response) => this.tokenStore.set(response.accessToken)),
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
      tap((response) => this.tokenStore.set(response.accessToken)),
      catchError((err) => throwError(() => new AppHttpError(parseApiError(err)))),
    );
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
