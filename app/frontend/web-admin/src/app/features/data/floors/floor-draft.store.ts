import type { FloorDraft } from './floor-draft';

/**
 * Unsaved floor edits, kept on this device until they are saved.
 *
 * <p>A floor is edited standing in it, on an iPad whose connection comes and goes and whose
 * browser may be closed mid-floor. Every change is written here, so a dropped connection, a
 * closed tab or a flat battery costs nothing that was already drawn. A save clears it.
 *
 * <p>Browser storage can be unavailable - a private window, storage blocked or full - and every
 * access is guarded: the editor then works as before, it just cannot promise the draft.
 */
export interface StoredDraft {
  /** The floor version the draft started from. Saving it means sending this back. */
  baseVersion: number;
  savedAt: string;
  draft: FloorDraft;
}

const PREFIX = 'kapp-admin:floor-draft:';

function keyFor(buildingCode: string, floorCode: string): string {
  return `${PREFIX}${buildingCode}:${floorCode}`;
}

export function loadDraft(buildingCode: string, floorCode: string): StoredDraft | null {
  try {
    const raw = localStorage.getItem(keyFor(buildingCode, floorCode));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredDraft;
    return parsed && typeof parsed.baseVersion === 'number' && parsed.draft ? parsed : null;
  } catch {
    return null;
  }
}

/** @return false when the draft could not be kept on this device */
export function storeDraft(buildingCode: string, floorCode: string, baseVersion: number, draft: FloorDraft): boolean {
  try {
    const stored: StoredDraft = { baseVersion, savedAt: new Date().toISOString(), draft };
    localStorage.setItem(keyFor(buildingCode, floorCode), JSON.stringify(stored));
    return true;
  } catch {
    return false;
  }
}

export function clearDraft(buildingCode: string, floorCode: string): void {
  try {
    localStorage.removeItem(keyFor(buildingCode, floorCode));
  } catch {
    // Nothing to clear if storage is unavailable.
  }
}
