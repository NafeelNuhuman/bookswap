import type { Proposal } from '../api/types';

export type ProposalActions = {
  accept: boolean;
  reject: boolean;
  counter: boolean;
  cancel: boolean;
  confirm: boolean;
  editPickupNote: boolean;
};

const NONE: ProposalActions = { accept: false, reject: false, counter: false, cancel: false, confirm: false, editPickupNote: false };

/**
 * Which buttons to show, from the fields the API returns. This mirrors the backend's rules only to hide actions it
 * would refuse; the backend still decides. After one confirmation only the party who has not confirmed may cancel.
 */
export function proposalActions(p: Proposal, myId: string, now: Date): ProposalActions {
  const iAmProposer = p.proposer.id === myId;
  const iAmRecipient = p.recipient.id === myId;
  if (!iAmProposer && !iAmRecipient) return NONE;
  const t = now.getTime();

  if (p.status === 'PROPOSED' && t < Date.parse(p.expiresAt)) {
    return iAmRecipient ? { ...NONE, accept: true, reject: true, counter: true } : { ...NONE, cancel: true };
  }
  if (p.status === 'ACCEPTED' && p.confirmBy && t < Date.parse(p.confirmBy)) {
    const notConfirmedByMe = p.confirmedByMe === false;
    return { ...NONE, confirm: notConfirmedByMe, cancel: notConfirmedByMe, editPickupNote: true };
  }
  return NONE;
}
