import {
  ApplicationConfig,
  ENVIRONMENT_INITIALIZER,
  inject,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeEsCo from '@angular/common/locales/es-CO';
import { NavigationError, Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { authInterceptor } from './core/auth/auth.interceptor';
import { routes } from './app.routes';

// Dates in Spanish: the date formatting the portal's localDate pipe does in its language.
registerLocaleData(localeEsCo);

/**
 * Recovers from a deploy that happened while the app was open.
 *
 * <p>Every route here is lazily loaded and every chunk is named by content hash, so the moment
 * a new build is published the chunks the running tab knows about stop existing. The next
 * navigation fails to import one, the router raises NavigationError, and the app silently stays
 * where it was - which is how "Sign out" managed to clear the session and leave somebody inside
 * the portal with no token and no way out.
 *
 * <p>A reload is the whole fix: it fetches index.html, which is served no-cache, which points at
 * the chunks that do exist. Only for import failures - a NavigationError from a guard or a
 * resolver means something else, and reloading on those would be a loop.
 */
function reloadOnStaleChunk(): void {
  const router = inject(Router);
  router.events.subscribe((event) => {
    if (!(event instanceof NavigationError)) {
      return;
    }
    const message = String((event.error as Error)?.message ?? event.error);
    if (/Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed/i.test(message)) {
      window.location.assign(event.url);
    }
  });
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    { provide: ENVIRONMENT_INITIALIZER, multi: true, useValue: reloadOnStaleChunk },
  ],
};
