export const MAX_PER_SIDE = 5;
export const MAX_MESSAGE = 500;

export type Side = 'requested' | 'offered';
export type Selection = { requested: string[]; offered: string[] };

/** Adds the copy to the side (up to MAX_PER_SIDE) or removes it when already there. */
export function toggle(sel: Selection, side: Side, copyId: string): Selection {
  const current = sel[side];
  if (current.includes(copyId)) return { ...sel, [side]: current.filter((id) => id !== copyId) };
  if (current.length >= MAX_PER_SIDE) return sel;
  return { ...sel, [side]: [...current, copyId] };
}

export const canSubmit = (sel: Selection): boolean =>
  sel.requested.length >= 1 && sel.requested.length <= MAX_PER_SIDE &&
  sel.offered.length >= 1 && sel.offered.length <= MAX_PER_SIDE;

export function toRequest(sel: Selection, message: string) {
  const text = message.trim();
  return { requestedCopyIds: sel.requested, offeredCopyIds: sel.offered, message: text ? text : null };
}
