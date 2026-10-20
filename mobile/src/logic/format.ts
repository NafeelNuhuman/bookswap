import type { ConditionGrade, CopyStatus, Item, ProposalStatus } from '../api/types';

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

const PROPOSAL_STATUSES: Record<ProposalStatus, string> = {
  PROPOSED: 'Waiting for a reply',
  ACCEPTED: 'Accepted',
  REJECTED: 'Declined',
  COUNTERED: 'Countered',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  COMPLETED: 'Completed',
};

export const proposalStatusLabel = (s: ProposalStatus): string => PROPOSAL_STATUSES[s] ?? s;

/** "Dune", or "Dune + 2 more" when there are several. */
export function summarizeItems(items: Item[]): string {
  if (items.length === 0) return '';
  return items.length === 1 ? items[0].title : `${items[0].title} + ${items.length - 1} more`;
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

/** The label to show now: a lapsed proposal reads as expired and an accepted one past its deadline as settling. */
export function effectiveStatusLabel(p: { status: ProposalStatus; expiresAt: string; confirmBy: string | null }, now: Date): string {
  if (p.status === 'PROPOSED' && Date.parse(p.expiresAt) <= now.getTime()) return 'Expired';
  if (p.status === 'ACCEPTED' && p.confirmBy && Date.parse(p.confirmBy) <= now.getTime()) return 'Settling soon';
  return proposalStatusLabel(p.status);
}
