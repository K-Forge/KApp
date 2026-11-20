import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MicrosoftSignInService } from '../../core/auth/microsoft-sign-in.service';
import { AppHttpError } from '../../core/http/api-http-error';
import type { ApiError } from '../../core/http/api-error.model';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ApiErrorBannerComponent } from '../../shared/ui/api-error-banner/api-error-banner.component';

/** The server's 403 for the portal and an account with no permission: it names `client`. */
export function noPortalPermission(error: ApiError | null): boolean {
  return error?.status === 403 && (error.details ?? []).some((issue) => issue.field === 'client');
}

/**
 * Where Microsoft sends the browser back after signing in. It finishes the sign-in and goes back to
 * the page the person was on, or says plainly why it could not.
 */
@Component({
  selector: 'app-microsoft-callback-page',
  imports: [TranslatePipe, RouterLink, ApiErrorBannerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="callback-shell">
      <div class="card callback-card">
        @if (failed()) {
          @if (noPermission()) {
            <div class="card api-error" role="alert">
              <strong>{{ 'This account has no permission for the portal.' | t }}</strong>
              <p>{{ 'Microsoft signed you in, but an administrator of KApp has not given this account any permission. Students and professors use the KApp app.' | t }}</p>
            </div>
          } @else if (message()) {
            <div class="card api-error" role="alert">
              <strong>{{ 'The sign-in with Microsoft did not finish.' | t }}</strong>
              <p>{{ message() }}</p>
            </div>
          }
          <app-api-error-banner [error]="noPermission() ? null : error()" />
          <a routerLink="/login" class="btn btn-primary">{{ 'Back to sign-in' | t }}</a>
        } @else {
          <p role="status">{{ 'Finishing the sign-in with Microsoft…' | t }}</p>
        }
      </div>
    </div>
  `,
  styles: `
    .callback-shell {
      min-height: 100dvh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
    }
    .callback-card {
      width: 100%;
      max-width: 24rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
  `,
})
export class MicrosoftCallbackPage implements OnInit {
  private readonly microsoft = inject(MicrosoftSignInService);
  private readonly router = inject(Router);

  readonly failed = signal(false);
  readonly noPermission = signal(false);
  readonly message = signal<string | null>(null);
  readonly error = signal<ApiError | null>(null);

  ngOnInit(): void {
    this.microsoft
      .complete(new URLSearchParams(window.location.search))
      .then((returnUrl) => this.router.navigateByUrl(returnUrl))
      .catch((err: unknown) => {
        const apiError = err instanceof AppHttpError ? err.apiError : null;
        this.noPermission.set(noPortalPermission(apiError));
        this.error.set(apiError);
        this.message.set(apiError ? null : err instanceof Error ? err.message : String(err));
        this.failed.set(true);
      });
  }
}
