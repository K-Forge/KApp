export interface PensumArea {
  code: string;
  name: string;
  color: string;
  credits: number;
  hours: number;
}

export interface PensumCourse {
  /** KApp's own identifier of the item, which prerequisites and plans name it by. Never shown. */
  pensumItemCode: string;
  name: string;
  level: number;
  credits: number;
  weeklyHours: number;
  totalHours?: number;
  area: string;
  isElectiveSlot: boolean;
  prerequisites: string[];
  /**
   * The course code as the university's own system carries it, or null where it is not known.
   *
   * <p>This is the only field that says a code is real. Nineteen of the twenty-three published
   * plans are brochures that print no codes at all, so their `pensumItemCode` is something KApp
   * made up (`MKT-101`) to tell the items apart. It is an internal handle, not an institutional
   * code, and it is never put on screen — see {@link officialCode}.
   */
  sinuCode?: string | null;
}

/**
 * The code to show for an item: the institutional one, or nothing.
 *
 * <p>A generated code on screen is worse than no code. It looks exactly like the real thing, so
 * a student would quote it to the registrar and an admin would check it against the PDF and find
 * nothing — and the day the real codes arrive, every one of them changes.
 */
export function officialCode(course: PensumCourse): string | null {
  return course.sinuCode ?? null;
}

/** True when the plan carries the university's own course codes rather than KApp's handles. */
export function publishesCourseCodes(pensum: Pensum): boolean {
  return pensum.courses.some((course) => !!course.sinuCode);
}

/** The items of a pensum by the identifier its prerequisites name them with. */
export function coursesByItemCode(pensum: Pensum): Map<string, PensumCourse> {
  return new Map(pensum.courses.map((c) => [c.pensumItemCode, c]));
}

/**
 * What an item's prerequisites are called on screen: their institutional code where there is
 * one, their name where there is not. A name is longer than a code and says the same thing to
 * whoever is checking the plan against its PDF, which a made-up code does not.
 */
export function prerequisiteLabels(
  byItemCode: Map<string, PensumCourse>,
  course: PensumCourse,
): string[] {
  return course.prerequisites.map((code) => {
    const target = byItemCode.get(code);
    // Unreachable through the API, which refuses a prerequisite naming no course in the same
    // pensum; a hand-edited document pasted into the editor is another matter.
    if (!target) return code;
    return officialCode(target) ?? target.name;
  });
}

/** Mirrors Pensum in docs/api/semaphore.openapi.yaml - a whole pensum document. */
export interface Pensum {
  pensumCode: string;
  programCode: string;
  programName: string;
  faculty: string;
  reform: string;
  status: 'ACTIVE' | 'DRAFT' | 'OBSOLETE';
  totalCredits: number;
  totalHours: number;
  levels: number;
  areas: PensumArea[];
  courses: PensumCourse[];
}

/**
 * A pensum without its courses — what `GET /api/catalog/pensums` returns.
 *
 * <p>Enough to fill a picker and a listing; the full document is one call away. Twenty-four
 * pensums of sixty courses each would be fifteen hundred objects a dropdown has no use for.
 */
export interface PensumSummary {
  pensumCode: string;
  programCode: string;
  programName: string;
  faculty: string;
  reform: string;
  status: 'DRAFT' | 'ACTIVE' | 'OBSOLETE';
  totalCredits: number;
  totalHours: number;
  levels: number;
  courses: number;
}
