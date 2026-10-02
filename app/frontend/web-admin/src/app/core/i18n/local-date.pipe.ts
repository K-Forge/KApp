import { Pipe, PipeTransform } from '@angular/core';
import { formatDate } from '@angular/common';
import { locale } from './i18n.service';

/**
 * `{{ at | localDate }}`: a date in the portal's language, "29 sept 2026, 15:42" or
 * "29 Sep 2026, 15:42". The date pipe keeps the locale the app started with; this follows the
 * language as it changes. Takes the date pipe's formats.
 */
@Pipe({ name: 'localDate', pure: false })
export class LocalDatePipe implements PipeTransform {
  transform(value: string | number | Date | null | undefined, format = 'dd MMM y, HH:mm'): string {
    return value === null || value === undefined || value === '' ? '' : formatDate(value, format, locale());
  }
}
