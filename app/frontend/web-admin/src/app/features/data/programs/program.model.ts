export const PROGRAM_LEVELS = [
  'PREGRADO',
  'POSGRADO',
  'TECNOLOGIA',
  'MAESTRIA',
  'DOCTORADO',
  'CURSOS_DIPLOMADOS',
  'ESPECIALIZACION',
] as const;

/** Mirrors Program in docs/api/semaphore.openapi.yaml. */
export interface Program {
  code: string;
  name: string;
  faculty: string;
  level: (typeof PROGRAM_LEVELS)[number];
  activePensumCode: string | null;
}
