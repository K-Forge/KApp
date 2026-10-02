import { Pipe, PipeTransform, inject } from '@angular/core';
import { I18nService } from './i18n.service';

/**
 * `{{ 'Save' | t }}`, `{{ 'Block {code}' | t: { code: block() } }}`: the text in the portal's
 * language. Impure so that it follows the language as it changes; it only looks a text up.
 */
@Pipe({ name: 't', pure: false })
export class TranslatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(text: string | null | undefined, params?: Record<string, unknown>): string {
    return text ? this.i18n.t(text, params) : '';
  }
}
