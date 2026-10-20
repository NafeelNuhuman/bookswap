import type { ConditionGrade, CopyStatus } from '../api/types';

// Mirror the backend's grade labels (GET /api/condition-grades) so lists need no extra request.
const GRADES: Record<ConditionGrade, string> = {
  NEW: 'New',
  LIKE_NEW: 'Like New',
  GOOD: 'Good',
  ACCEPTABLE: 'Acceptable',
  POOR: 'Poor',
};

const STATUSES: Record<CopyStatus, string> = {
  AVAILABLE: 'Available',
  UNLISTED: 'Unlisted',
  RESERVED: 'Reserved',
};

export const gradeLabel = (g: ConditionGrade): string => GRADES[g] ?? g;
export const statusLabel = (s: CopyStatus): string => STATUSES[s] ?? s;

export function ownerLine(owner: { displayName: string; city?: string | null }): string {
  const city = owner.city?.trim();
  return city ? `${owner.displayName} · ${city}` : owner.displayName;
}
