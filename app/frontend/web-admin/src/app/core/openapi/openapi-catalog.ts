import authSpec from './generated/auth.json';
import userSpec from './generated/user.json';
import semaphoreSpec from './generated/semaphore.json';
import scheduleSpec from './generated/schedule.json';
import mapSpec from './generated/map.json';
import type { OpenApiDocument } from './openapi.types';

export interface ServiceDescriptor {
  id: string;
  label: string;
  /** Prism mock port from docker-compose.yml's `mock` profile (auth 4010 .. map 4014). */
  mockPort: number;
  doc: OpenApiDocument;
}

// Bundled at build time (see scripts/generate-openapi.mjs) rather than fetched, so the console
// and role inspector work the instant the app loads - no extra round trip, and no risk of the
// portal shipping without the contracts it exists to exercise.
export const SERVICES: ServiceDescriptor[] = [
  { id: 'auth', label: /* i18n */ 'Auth', mockPort: 4010, doc: authSpec as unknown as OpenApiDocument },
  { id: 'user', label: /* i18n */ 'User', mockPort: 4011, doc: userSpec as unknown as OpenApiDocument },
  { id: 'semaphore', label: /* i18n */ 'Semaphore', mockPort: 4012, doc: semaphoreSpec as unknown as OpenApiDocument },
  { id: 'schedule', label: /* i18n */ 'Schedule', mockPort: 4013, doc: scheduleSpec as unknown as OpenApiDocument },
  { id: 'map', label: /* i18n */ 'Map', mockPort: 4014, doc: mapSpec as unknown as OpenApiDocument },
];
