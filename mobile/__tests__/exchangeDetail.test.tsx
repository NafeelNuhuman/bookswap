import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import ExchangeDetailScreen from '../app/(tabs)/exchanges/[id]';
import { apiError, item, party, proposal } from './fixtures';
import { Wrapper, fakeFetch, memoryTokens, Route } from './testUtils';

const mockPush = jest.fn();
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn(), replace: jest.fn() }),
  useLocalSearchParams: () => ({ id: 'p1' }),
}));

const me = { id: 'me', email: 'me@x.com', displayName: 'Me', city: 'Leeds', postalCode: 'LS1', contactNote: null, profileComplete: true };
const FUTURE = '2099-01-01T00:00:00Z';

async function renderWith(p: ReturnType<typeof proposal>, extra: Route = () => undefined) {
  const f = fakeFetch((m, path, b) => {
    if (m === 'GET' && path === '/api/users/me') return { status: 200, body: me };
    if (m === 'GET' && path === '/api/proposals/p1') return { status: 200, body: p };
    return extra(m, path, b);
  });
  await render(<Wrapper fetchImpl={f.impl} tokenStore={memoryTokens({ access: 'a', refresh: 'r' })}><ExchangeDetailScreen /></Wrapper>);
  return f;
}

const accepted = (over = {}) => proposal({
  status: 'ACCEPTED', confirmBy: FUTURE, confirmedByMe: false, confirmedByOther: false, acceptedAt: '2026-10-02T00:00:00Z', ...over,
});

beforeEach(() => mockPush.mockClear());

describe('exchange detail: privacy', () => {
  it("shows the other party's email and contact note when the API sends them", async () => {
    await renderWith(accepted({ proposer: party('u2', 'Sam', { email: 'sam@x.com', contactNote: 'Call me after 5' }) }));
    expect(await screen.findByText('sam@x.com')).toBeTruthy();
    expect(screen.getByText('Call me after 5')).toBeTruthy();
  });

  it('shows no contact block when the API sends none', async () => {
    await renderWith(proposal({ status: 'PROPOSED', expiresAt: FUTURE }));
    expect(await screen.findByText('Sam')).toBeTruthy();
    expect(screen.queryByText('Contact details')).toBeNull();
  });
});

describe('exchange detail: actions', () => {
  it('confirm calls POST /confirm and then says it is waiting for the other person', async () => {
    const f = await renderWith(accepted(), (m, p) => (m === 'POST' && p === '/api/proposals/p1/confirm'
      ? { status: 200, body: accepted({ confirmedByMe: true }) } : undefined));
    await fireEvent.press(await screen.findByRole('button', { name: 'Confirm the swap' }));
    expect(await screen.findByText('Waiting for the other person to confirm.')).toBeTruthy();
    expect(f.calls.some((c) => c.method === 'POST' && c.path === '/api/proposals/p1/confirm')).toBe(true);
    expect(screen.queryByRole('button', { name: 'Cancel exchange' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Confirm the swap' })).toBeNull();
  });

  it('ALREADY_CONFIRMED shows the friendly message', async () => {
    await renderWith(accepted(), (m, p) => (m === 'POST' ? apiError(409, 'ALREADY_CONFIRMED') : undefined));
    await fireEvent.press(await screen.findByRole('button', { name: 'Confirm the swap' }));
    expect(await screen.findByText("You've already confirmed this exchange.")).toBeTruthy();
  });

  it('accept calls POST /accept', async () => {
    const f = await renderWith(proposal({ status: 'PROPOSED', expiresAt: FUTURE }), (m, p) => (p === '/api/proposals/p1/accept'
      ? { status: 200, body: accepted() } : undefined));
    await fireEvent.press(await screen.findByRole('button', { name: 'Accept' }));
    await waitFor(() => expect(f.calls.some((c) => c.path === '/api/proposals/p1/accept')).toBe(true));
  });

  it('decline asks for an optional reason and sends it', async () => {
    const f = await renderWith(proposal({ status: 'PROPOSED', expiresAt: FUTURE }), (m, p) => (p === '/api/proposals/p1/reject'
      ? { status: 200, body: proposal({ status: 'REJECTED' }) } : undefined));
    await fireEvent.press(await screen.findByRole('button', { name: 'Decline' }));
    await fireEvent.changeText(screen.getByLabelText('Reason (optional)'), 'Not my taste');
    await fireEvent.press(screen.getByRole('button', { name: 'Send decline' }));
    await waitFor(() => expect(f.calls.find((c) => c.path === '/api/proposals/p1/reject')?.body).toEqual({ reason: 'Not my taste' }));
  });

  it('counter-offer navigates to compose with counterOf', async () => {
    await renderWith(proposal({ status: 'PROPOSED', expiresAt: FUTURE }));
    await fireEvent.press(await screen.findByRole('button', { name: 'Counter-offer' }));
    expect(mockPush).toHaveBeenCalledWith({ pathname: '/compose', params: { counterOf: 'p1' } });
  });

  it('cancelling an accepted exchange asks first, then POSTs /cancel', async () => {
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    const f = await renderWith(accepted(), (m, p) => (p === '/api/proposals/p1/cancel'
      ? { status: 200, body: proposal({ status: 'CANCELLED', cancelCause: 'WITHDRAWN' }) } : undefined));
    await fireEvent.press(await screen.findByRole('button', { name: 'Cancel exchange' }));
    expect(f.calls.some((c) => c.path === '/api/proposals/p1/cancel')).toBe(false);
    const buttons = alert.mock.calls[0][2] as { text: string; onPress?: () => void }[];
    await buttons.find((b) => b.text === 'Cancel exchange')!.onPress!();
    await waitFor(() => expect(f.calls.some((c) => c.method === 'POST' && c.path === '/api/proposals/p1/cancel')).toBe(true));
    alert.mockRestore();
  });

  it('saving a pickup note PUTs the text, and empty text clears it', async () => {
    const f = await renderWith(accepted({ pickupNote: 'Cafe at 5' }), (m, p) => (m === 'PUT' ? { status: 200, body: accepted() } : undefined));
    const field = await screen.findByLabelText('Where and when to meet');
    expect(field.props.value).toBe('Cafe at 5');
    await fireEvent.changeText(field, '');
    await fireEvent.press(screen.getByRole('button', { name: 'Save note' }));
    await waitFor(() => expect(f.calls.find((c) => c.method === 'PUT')?.body).toEqual({ note: '' }));
  });
});

describe('exchange detail: closed proposals', () => {
  it('a COUNTERED proposal shows its status and no actions', async () => {
    await renderWith(proposal({ status: 'COUNTERED' }));
    expect(await screen.findByText('Countered')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Accept' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Cancel proposal' })).toBeNull();
  });

  it('a completed exchange shows what changed hands', async () => {
    await renderWith(proposal({ status: 'COMPLETED', completedAt: '2026-10-05T00:00:00Z',
      offered: [item('k1', 'Dune')], requested: [item('m1', 'Mine One')] }));
    expect(await screen.findByText('You received')).toBeTruthy();
    expect(screen.getByText('Dune')).toBeTruthy();
    expect(screen.getByText('You gave')).toBeTruthy();
    expect(screen.getByText('Mine One')).toBeTruthy();
  });

  it('a rejected proposal shows the reason', async () => {
    await renderWith(proposal({ status: 'REJECTED', rejectReason: 'Not my taste' }));
    expect(await screen.findByText('Not my taste')).toBeTruthy();
  });
});
