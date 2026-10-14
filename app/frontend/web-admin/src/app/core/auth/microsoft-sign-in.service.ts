import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiConfigService } from '../config/api-config.service';
import { AppHttpError } from '../http/api-http-error';
import { parseApiError } from '../http/api-error.util';
import { t } from '../i18n/i18n.service';
import type { TokenResponse } from './auth.model';
import { TokenStore } from './token.store';

/** Mirrors SignInOptions in docs/api/auth.openapi.yaml. */
export interface SignInOptions {
  microsoft: { authority: string; clientId: string } | null;
  localSignIn: boolean;
}

/** What survives the trip to Microsoft and back: only this tab, only until it returns. */
interface PendingSignIn {
  state: string;
  nonce: string;
  verifier: string;
  tokenEndpoint: string;
  clientId: string;
  redirectUri: string;
  returnUrl: string;
}

const PENDING_KEY = 'kapp-admin:microsoft-sign-in';

/** Where Microsoft sends the browser back, on the portal's own origin. */
export const CALLBACK_PATH = '/auth/callback';

/**
 * Signing in to the portal with Microsoft: OpenID Connect with an authorization code and PKCE, the
 * flow Microsoft documents for single-page applications. No secret is involved anywhere.
 *
 * <ol>
 *   <li>{@link start} asks auth-service where to sign in, keeps a fresh verifier, state and nonce in
 *       this tab, and sends the browser to Microsoft.</li>
 *   <li>Microsoft sends it back to {@link CALLBACK_PATH}; {@link complete} checks the state, swaps the
 *       code for an ID token, checks the nonce in it, and hands the ID token to {@code POST
 *       /auth/microsoft} as the portal. KApp's own tokens come back.</li>
 * </ol>
 *
 * <p>The state stops a sign-in somebody else started from landing here; the nonce stops an ID token
 * from another sign-in being replayed into this one. auth-service checks the token itself.
 */
@Injectable({ providedIn: 'root' })
export class MicrosoftSignInService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApiConfigService);
  private readonly tokenStore = inject(TokenStore);

  /** How to sign in to the server the portal points at. */
  options(): Promise<SignInOptions> {
    return firstValueFrom(this.http.get<SignInOptions>(`${this.config.baseUrl()}/auth/sign-in-options`));
  }

  /** Leaves for Microsoft's sign-in. Resolves only if it cannot leave. */
  async start(microsoft: { authority: string; clientId: string }, returnUrl: string): Promise<void> {
    const discovery = await firstValueFrom(
      this.http.get<{ authorization_endpoint: string; token_endpoint: string }>(
        `${microsoft.authority.replace(/\/+$/, '')}/.well-known/openid-configuration`,
      ),
    );
    const pending: PendingSignIn = {
      state: randomToken(),
      nonce: randomToken(),
      verifier: randomToken(),
      tokenEndpoint: discovery.token_endpoint,
      clientId: microsoft.clientId,
      redirectUri: `${window.location.origin}${CALLBACK_PATH}`,
      returnUrl,
    };
    sessionStorage.setItem(PENDING_KEY, JSON.stringify(pending));

    const query = new URLSearchParams({
      client_id: pending.clientId,
      response_type: 'code',
      redirect_uri: pending.redirectUri,
      scope: 'openid profile email',
      state: pending.state,
      nonce: pending.nonce,
      code_challenge: await challengeOf(pending.verifier),
      code_challenge_method: 'S256',
      prompt: 'select_account',
    });
    window.location.assign(`${discovery.authorization_endpoint}?${query}`);
  }

  /**
   * Finishes a sign-in Microsoft sent back here.
   *
   * @returns where to go now, the page the person was on before signing in
   * @throws AppHttpError with the server's answer: 401 for a token that does not hold, 403 for an
   *         account with no permission or a deactivated one, 503 when it cannot be checked now
   */
  async complete(query: URLSearchParams): Promise<string> {
    const pending = takePending();
    const failure = query.get('error_description') ?? query.get('error');
    if (failure) {
      throw new Error(failure);
    }
    if (!pending || !query.get('state') || query.get('state') !== pending.state) {
      throw new Error(t('This sign-in was not started here, or it was already used. Start it again.'));
    }

    const tokens = await fetch(pending.tokenEndpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'authorization_code',
        code: query.get('code') ?? '',
        redirect_uri: pending.redirectUri,
        client_id: pending.clientId,
        code_verifier: pending.verifier,
      }),
    }).then((response) => response.json() as Promise<{ id_token?: string; error_description?: string }>);
    if (!tokens.id_token) {
      throw new Error(tokens.error_description ?? t('Microsoft gave no ID token.'));
    }
    if (nonceOf(tokens.id_token) !== pending.nonce) {
      throw new Error(t('The ID token belongs to another sign-in. Start it again.'));
    }

    try {
      const response = await firstValueFrom(
        this.http.post<TokenResponse>(`${this.config.baseUrl()}/auth/microsoft`, {
          idToken: tokens.id_token,
          client: 'PORTAL',
        }),
      );
      this.tokenStore.start(response);
    } catch (err) {
      throw new AppHttpError(parseApiError(err));
    }
    return pending.returnUrl;
  }
}

/** 32 random bytes, base64url: enough for a state, a nonce or a PKCE verifier. */
export function randomToken(): string {
  return base64Url(crypto.getRandomValues(new Uint8Array(32)));
}

/** The PKCE challenge of a verifier: base64url of its SHA-256 (RFC 7636, S256). */
export async function challengeOf(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

/** The nonce claim of an ID token. Only compared, never trusted: auth-service checks the token. */
export function nonceOf(idToken: string): string | undefined {
  try {
    const payload = idToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '='))).nonce;
  } catch {
    return undefined;
  }
}

/** The pending sign-in, removed as it is read: a state can be used once. */
function takePending(): PendingSignIn | null {
  const raw = sessionStorage.getItem(PENDING_KEY);
  sessionStorage.removeItem(PENDING_KEY);
  try {
    return raw ? (JSON.parse(raw) as PendingSignIn) : null;
  } catch {
    return null;
  }
}

function base64Url(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((byte) => (binary += String.fromCharCode(byte)));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
