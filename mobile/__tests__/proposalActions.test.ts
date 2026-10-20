import { proposalActions } from '../src/logic/proposalActions';
import { party, proposal } from './fixtures';
import type { Proposal } from '../src/api/types';

const NOW = new Date('2026-10-10T12:00:00Z');
const FUTURE = '2026-10-20T12:00:00Z';
const PAST = '2026-10-01T12:00:00Z';

const none = { accept: false, reject: false, counter: false, cancel: false, confirm: false, editPickupNote: false };
const asRecipient = (over: Partial<Proposal>) => proposalActions(proposal({ ...over }), 'me', NOW);
const asProposer = (over: Partial<Proposal>) =>
  proposalActions(proposal({ proposer: party('me', 'Me'), recipient: party('u2', 'Sam'), ...over }), 'me', NOW);
const accepted = (confirmedByMe: boolean, confirmedByOther: boolean, confirmBy = FUTURE): Partial<Proposal> =>
  ({ status: 'ACCEPTED', confirmBy, confirmedByMe, confirmedByOther, expiresAt: PAST });

describe('proposalActions', () => {
  it('PROPOSED: the recipient can accept, reject and counter; the proposer can only cancel', () => {
    const open = { status: 'PROPOSED' as const, expiresAt: FUTURE };
    expect(asRecipient(open)).toEqual({ ...none, accept: true, reject: true, counter: true });
    expect(asProposer(open)).toEqual({ ...none, cancel: true });
  });

  it('PROPOSED past its expiry offers nothing', () => {
    const lapsed = { status: 'PROPOSED' as const, expiresAt: PAST };
    expect(asRecipient(lapsed)).toEqual(none);
    expect(asProposer(lapsed)).toEqual(none);
  });

  it('ACCEPTED, nobody confirmed: either party may confirm, cancel and edit the pickup note', () => {
    const both = { ...none, confirm: true, cancel: true, editPickupNote: true };
    expect(asRecipient(accepted(false, false))).toEqual(both);
    expect(asProposer(accepted(false, false))).toEqual(both);
  });

  it('the confirmer cannot cancel or confirm again, but can still edit the note', () => {
    const a = asRecipient(accepted(true, false));
    expect(a).toEqual({ ...none, editPickupNote: true });
    expect(a.cancel).toBe(false);
    expect(asProposer(accepted(true, false))).toEqual({ ...none, editPickupNote: true });
  });

  it('after the other party confirmed, I may still confirm or cancel (a dispute)', () => {
    expect(asRecipient(accepted(false, true))).toEqual({ ...none, confirm: true, cancel: true, editPickupNote: true });
  });

  it('nothing is allowed at exactly confirmBy, nor after it', () => {
    expect(asRecipient(accepted(false, false, NOW.toISOString()))).toEqual(none);
    expect(asRecipient(accepted(false, false, PAST))).toEqual(none);
    const oneTickBefore = new Date(NOW.getTime() + 1).toISOString();
    expect(asRecipient(accepted(false, false, oneTickBefore)).confirm).toBe(true);
  });

  it.each(['REJECTED', 'COUNTERED', 'CANCELLED', 'EXPIRED', 'COMPLETED'] as const)('%s offers nothing to either party', (status) => {
    expect(asRecipient({ status })).toEqual(none);
    expect(asProposer({ status })).toEqual(none);
  });

  it('a stranger (neither party) is offered nothing', () => {
    expect(proposalActions(proposal({ status: 'ACCEPTED', confirmBy: FUTURE, confirmedByMe: false, confirmedByOther: false }), 'someone-else', NOW)).toEqual(none);
  });
});
