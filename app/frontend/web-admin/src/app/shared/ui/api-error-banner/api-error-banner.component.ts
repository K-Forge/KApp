import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { ApiError } from '../../../core/http/api-error.model';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { serverText } from '../../../core/i18n/server-messages';

/**
 * The one place an ApiError gets rendered. Every feature passes its caught error here instead of
 * writing its own "something went wrong" - the point of parsing the envelope centrally is that a
 * validation message and its field-level details always look the same, wherever they came from.
 */
@Component({
  selector: 'app-api-error-banner',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error(); as err) {
      <div class="card api-error" role="alert">
        <div class="row-between">
          <strong>{{ titleOf(err) | t }} ({{ err.status }})</strong>
          <span class="text-faint mono">{{ err.path }}</span>
        </div>
        <p class="api-error-message">{{ said(err.message) }}</p>
        @if (err.details?.length) {
          <ul class="api-error-details">
            @for (detail of err.details; track $index) {
              <li><strong>{{ detail.field }}</strong>: {{ said(detail.issue) }}</li>
            }
          </ul>
        }
      </div>
    }
  `,
  styles: `
    .api-error {
      border-color: var(--danger);
      background: var(--danger-bg);
      color: var(--danger-strong);
    }
    .api-error-message {
      margin: 0.5rem 0 0;
    }
    .api-error-details {
      margin: 0.5rem 0 0;
      padding-left: 1.25rem;
    }
  `,
})
export class ApiErrorBannerComponent {
  readonly error = input<ApiError | null>(null);

  /** The service's own words, in the portal's language when the portal knows them. */
  said(message: string | null | undefined): string {
    return serverText(message);
  }

  /** What went wrong, said by its status: the server's own title is English and terse. */
  titleOf(err: ApiError): string {
    return STATUS_TITLES[err.status] ?? (err.status >= 500 ? STATUS_TITLES[500] : /* i18n */ 'The server refused the request');
  }
}

const STATUS_TITLES: Record<number, string> = {
  0: /* i18n */ 'The server could not be reached',
  400: /* i18n */ 'Something in the request is not right',
  401: /* i18n */ 'The session is not valid',
  403: /* i18n */ 'This account may not do that',
  404: /* i18n */ 'Not found',
  409: /* i18n */ 'Somebody saved first',
  413: /* i18n */ 'Too large',
  422: /* i18n */ 'Something in the request is not right',
  429: /* i18n */ 'Too many attempts',
  500: /* i18n */ 'The server failed',
  502: /* i18n */ 'The server is not answering',
  503: /* i18n */ 'The server is not available',
  504: /* i18n */ 'The server took too long',
};
