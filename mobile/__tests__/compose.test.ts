import { MAX_PER_SIDE, canSubmit, toRequest, toggle } from '../src/logic/compose';

const empty = { requested: [], offered: [] };

describe('compose selection', () => {
  it('toggle adds and removes', () => {
    const a = toggle(empty, 'offered', 'm1');
    expect(a.offered).toEqual(['m1']);
    expect(toggle(a, 'offered', 'm1').offered).toEqual([]);
    expect(a.requested).toEqual([]);
  });

  it('toggle refuses a 6th item per side but still allows removing', () => {
    let sel = empty as { requested: string[]; offered: string[] };
    for (const id of ['1', '2', '3', '4', '5', '6']) sel = toggle(sel, 'requested', id);
    expect(MAX_PER_SIDE).toBe(5);
    expect(sel.requested).toEqual(['1', '2', '3', '4', '5']);
    expect(toggle(sel, 'requested', '3').requested).toEqual(['1', '2', '4', '5']);
  });

  it('canSubmit needs at least one on each side', () => {
    expect(canSubmit(empty)).toBe(false);
    expect(canSubmit({ requested: ['k'], offered: [] })).toBe(false);
    expect(canSubmit({ requested: [], offered: ['m'] })).toBe(false);
    expect(canSubmit({ requested: ['k'], offered: ['m'] })).toBe(true);
  });

  it('toRequest turns a blank message into null and trims', () => {
    expect(toRequest({ requested: ['k'], offered: ['m'] }, '   ')).toEqual({ requestedCopyIds: ['k'], offeredCopyIds: ['m'], message: null });
    expect(toRequest({ requested: ['k'], offered: ['m'] }, ' hi ').message).toBe('hi');
  });
});
