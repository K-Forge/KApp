import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormControl, FormGroup, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ApiConfigService } from '../../core/config/api-config.service';
import { AppHttpError } from '../../core/http/api-http-error';
import type { ApiError } from '../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../shared/ui/api-error-banner/api-error-banner.component';

interface LoginForm {
  email: FormControl<string>;
  password: FormControl<string>;
}

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, ApiErrorBannerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="login-shell">
      <div class="card login-card">
        <!-- The same lockup as the shell's header, so signing in and being signed in look like
             the same product. The explanation that used to sit here is gone: nobody reaching
             this screen needs to be told what a token is before they can type a password. -->
        <div class="login-brand">
          <img src="/konrad-logo.png" alt="Fundación Universitaria Konrad Lorenz" width="44" height="44" />
          <div>
            <h1>KApp</h1>
            <p class="login-sub">Admin Portal</p>
          </div>
        </div>

        @if (sessionExpired()) {
          <div class="card expired" role="alert">
            <strong>Your session expired.</strong>
            <p>The portal signs out as soon as the token runs out. Sign in again to go back to where you were.</p>
          </div>
        }

        @if (refusedAsNonAdmin()) {
          <div class="card api-error" role="alert">
            <strong>That account is not an administrator.</strong>
            <p style="margin:0.35rem 0 0">The sign-in worked; this portal is admin-only.</p>
          </div>
        }

        <form [formGroup]="form" (ngSubmit)="submit()" class="stack">
          <div class="field" [class.invalid]="isInvalid('email')">
            <label for="email">E-mail</label>
            <input id="email" type="email" formControlName="email" autocomplete="username" />
            @if (isInvalid('email')) {
              <span class="error">Enter a valid e-mail address.</span>
            }
          </div>

          <div class="field" [class.invalid]="isInvalid('password')">
            <label for="password">Password</label>
            <input id="password" type="password" formControlName="password" autocomplete="current-password" />
            @if (isInvalid('password')) {
              <span class="error">Password is required.</span>
            }
          </div>

          <button type="submit" class="btn btn-primary" [disabled]="form.invalid || submitting()">
            {{ submitting() ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>

        <app-api-error-banner [error]="error()" />

        <details class="settings">
          <summary>Gateway</summary>
          <div class="field" style="margin-top: 0.75rem">
            <label for="base-url">Base URL</label>
            <input id="base-url" type="text" [value]="baseUrl()" (change)="onBaseUrlChange($event)" placeholder="http://localhost:8080" />
            <span class="hint">Saved in this browser only.</span>
          </div>
        </details>
      </div>
    </div>
  `,
  styles: `
    .login-shell {
      min-height: 100dvh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
    }
    .login-card {
      width: 100%;
      max-width: 24rem;
      padding-top: 1.75rem;
      position: relative;
      overflow: hidden;
      box-shadow: var(--shadow-md);
    }
    /* The institutional three, along the top edge - the same signature the header carries. */
    .login-card::before {
      content: '';
      position: absolute;
      inset: 0 0 auto 0;
      height: 3px;
      background: linear-gradient(
        90deg,
        var(--brand-teal) 0 38%,
        var(--brand-pink) 38% 72%,
        var(--brand-green) 72% 100%
      );
    }
    .login-brand {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      margin-bottom: 1.5rem;
    }
    .login-brand img {
      flex: 0 0 auto;
    }
    .login-brand h1 {
      margin: 0;
      font-size: 1.35rem;
      letter-spacing: -0.01em;
    }
    .login-sub {
      margin: 0;
      font-size: 0.7rem;
      font-weight: 600;
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--text-muted);
    }
    .expired {
      margin-bottom: 1rem;
      padding: 0.75rem 0.9rem;
      background: var(--warning-bg);
      color: var(--warning);
      border-color: color-mix(in srgb, var(--warning) 35%, transparent);
      box-shadow: none;
    }
    .expired p {
      margin: 0.35rem 0 0;
      color: var(--text);
    }
    .settings {
      margin-top: 1.5rem;
      font-size: 0.8125rem;
      color: var(--text-muted);
    }
    .settings summary {
      cursor: pointer;
      font-weight: 600;
    }
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly config = inject(ApiConfigService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /**
   * Set by the route guard when a valid but non-administrator token was turned away. Without
   * it, signing in correctly and landing back here reads as a broken login rather than a
   * refusal.
   *
   * <p>Read from the live query parameters, not from `route.snapshot`. The guard redirects
   * here from a page this component was already mounted behind, so Angular reuses the instance
   * and never re-runs the constructor — a snapshot read once at construction stays whatever it
   * was when the user first opened the page, which is to say empty, and the message never
   * appears at the one moment it exists for.
   */
  protected readonly refusedAsNonAdmin = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('reason') === 'admin-only')),
    { initialValue: false },
  );

  /** Set when the session ended because its token ran out, rather than by signing out. */
  protected readonly sessionExpired = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('reason') === 'expired')),
    { initialValue: false },
  );

  readonly form = new FormGroup<LoginForm>({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly submitting = signal(false);
  readonly error = signal<ApiError | null>(null);
  readonly baseUrl = this.config.baseUrl;

  isInvalid(name: keyof LoginForm): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  onBaseUrlChange(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.config.setBaseUrl(value);
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();

    this.auth.login(email, password).subscribe({
      next: () => {
        this.submitting.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/my-token';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }
}
