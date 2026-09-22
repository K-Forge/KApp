/** The columns the import CSV needs for each pensum item. */
export const ITEM_FIELDS = [
  { key: 'pensumItemCode', label: 'Item code', required: true, hint: 'Unique within the pensum' },
  { key: 'courseCode', label: 'Course code', required: false, hint: 'Empty for an elective slot' },
  { key: 'courseName', label: 'Name', required: true, hint: '' },
  { key: 'courseLevel', label: 'Level', required: true, hint: 'Semester, 1–12' },
  { key: 'credits', label: 'Credits', required: true, hint: '' },
  { key: 'weeklyHours', label: 'Weekly hours', required: true, hint: '' },
  { key: 'areaCode', label: 'Area', required: true, hint: 'The semáforo row' },
  { key: 'prerequisites', label: 'Prerequisites', required: false, hint: 'Course codes, separated by ; or ,' },
  { key: 'isElectiveSlot', label: 'Elective?', required: false, hint: 'true / false / sí / no' },
] as const;

export type ItemField = (typeof ITEM_FIELDS)[number]['key'];

/** One parsed row: the raw cells, plus what each maps to once the columns are assigned. */
export interface PastedRow {
  cells: string[];
}

export interface PensumHeader {
  programCode: string;
  programName: string;
  faculty: string;
  programLevel: string;
  pensumCode: string;
  reform: string;
  pensumStatus: string;
  declaredCredits: number | null;
  declaredHours: number | null;
  levels: number | null;
}

export const PROGRAM_LEVELS = [
  'PREGRADO', 'POSGRADO', 'TECNOLOGIA', 'MAESTRIA', 'DOCTORADO', 'CURSOS_DIPLOMADOS', 'ESPECIALIZACION',
] as const;

export const PENSUM_STATUSES = ['DRAFT', 'ACTIVE', 'OBSOLETE'] as const;

/**
 * Splits pasted text into a grid.
 *
 * <p>Copying a table out of a PDF yields one of two things: real tab characters, when the PDF
 * has a proper table structure, or runs of spaces, when it does not. Tabs are checked first
 * because they are unambiguous; a run of two or more spaces is the fallback, which is why a
 * course name with a single space in it survives and one padded into a column does not lose
 * its words.
 */
export function parsePaste(text: string): PastedRow[] {
  return text
    .split(/\r?\n/)
    .map((line) => line.trimEnd())
    .filter((line) => line.trim().length > 0)
    .map((line) => ({
      cells: (line.includes('\t') ? line.split('\t') : line.split(/ {2,}/)).map((c) => c.trim()),
    }));
}

/**
 * What a column is called, in the words the Konrad pensums actually use.
 *
 * <p>Order matters: the first pattern that matches a heading wins, so the specific ones come
 * before the general. "Prerrequisitos" has to be tried before "código" for a heading like
 * "código del prerrequisito", and a bare "código" means the COURSE code — the item code is
 * the numbering the pensum itself carries ("ítem", "nro", "consecutivo"), which is a different
 * thing and is usually absent from the PDF.
 */
const HEADINGS: ReadonlyArray<{ field: ItemField; test: RegExp }> = [
  { field: 'prerequisites', test: /(pre ?rre?quisit|requisit)/ },
  { field: 'pensumItemCode', test: /^(item|nro|no|num|consecutivo|#|orden)/ },
  { field: 'isElectiveSlot', test: /(electiv|tipo)/ },
  { field: 'courseName', test: /(asignatura|materia|nombre|curso|descripcion)/ },
  { field: 'courseLevel', test: /(nivel|semestre|periodo)/ },
  { field: 'credits', test: /(credito|^cr$)/ },
  { field: 'weeklyHours', test: /(hora|htd|hti|intensidad)/ },
  { field: 'areaCode', test: /(area|componente|nucleo|ciclo)/ },
  { field: 'courseCode', test: /(codigo|^cod|clave)/ },
];

/** Lower case, no accents, no punctuation — so "Créditos:" and "CREDITOS" are one word. */
function normalizeHeading(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9# ]+/g, ' ')
    .trim();
}

/** The field a single heading names, or undefined if it names nothing we know. */
export function fieldForHeading(heading: string): ItemField | undefined {
  const text = normalizeHeading(heading);
  if (!text) return undefined;
  return HEADINGS.find((h) => h.test.test(text))?.field;
}

/**
 * Whether the first pasted line is the table's heading rather than its first course.
 *
 * <p>Wrong in the wrong direction is expensive: dropping a real course loses it silently. So the
 * bar is two recognised headings AND not a single purely numeric cell — every real course row
 * carries a level, credits and hours, so a row with no number in it is not a course.
 */
export function looksLikeHeadingRow(cells: string[]): boolean {
  const filled = cells.filter((c) => c.trim().length > 0);
  if (filled.length < 2) return false;
  if (filled.some((c) => /^\d+([.,]\d+)?$/.test(c.trim()))) return false;
  return filled.filter((c) => fieldForHeading(c) !== undefined).length >= 2;
}

/** Maps columns by what the heading row calls them. A field can only come from one column. */
export function mapFromHeadings(cells: string[]): Record<number, ItemField | undefined> {
  const out: Record<number, ItemField | undefined> = {};
  const used = new Set<ItemField>();
  cells.forEach((cell, index) => {
    const field = fieldForHeading(cell);
    if (field && !used.has(field)) {
      out[index] = field;
      used.add(field);
    }
  });
  return out;
}

/**
 * Weekly hours as a plan prints them: a whole number, or a whole number and a half.
 *
 * <p>Four items across the published plans print a half — Psicología's two professional
 * practices at 4,5 hours and the practices of Marketing and Negocios Internacionales at 1,5 —
 * and nothing prints anything finer. A `4,3` in a pasted table is a misread cell, and the
 * server refuses it, so it is worth saying here rather than at the end of the import.
 */
export function parseHours(value: string): number {
  const text = value.trim().replace(',', '.');
  return /^\d{1,3}(\.5)?$/.test(text) ? Number(text) : NaN;
}

/** True/false in the forms a person actually types, including Spanish. */
export function looksTrue(value: string): boolean {
  return ['true', '1', 'si', 'sí', 'yes', 'x', 'electiva'].includes(value.trim().toLowerCase());
}

/** RFC 4180 quoting: only what needs it, so the file stays readable in a spreadsheet. */
export function csvCell(value: string): string {
  return /[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}
