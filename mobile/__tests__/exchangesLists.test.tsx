import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import ExchangesScreen from '../app/(tabs)/exchanges/index';
import { item, page, party, proposal } from './fixtures';
import { Wrapper, fakeFetch, memoryTokens, Route } from './testUtils';

const mockPush = jest.fn();
let mockParams: Record<string, string> = {};
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn(), replace: jest.fn() }),
  useLocalSearchParams: () => mockParams,
}));

async function renderWith(route: Route) {
  const f = fakeFetch(route);
  await render(<Wrapper fetchImpl={f.impl} tokenStore={memoryTokens({ access: 'a', refresh: 'r' })}><ExchangesScreen /></Wrapper>);
  return f;
}

const listCall = (f: ReturnType<typeof fakeFetch>, needle: string) => f.calls.some((c) => c.path.startsWith('/api/proposals') && c.path.includes(needle));

beforeEach(() => { mockPush.mockClear(); mockParams = {}; });

describe('exchanges tab', () => {
  it('Incoming requests role=incoming and shows the proposer; Outgoing requests role=outgoing', async () => {
    const f = await renderWith((m, p) => {
      if (p !== '/api/proposals') return undefined;
      return { status: 200, body: page([proposal({ id: 'in1', proposer: party('u2', 'Sam'), offered: [item('k1', 'Dune'), item('k3', 'Emma')] })]) };
    });
    expect(await screen.findByText('Sam')).toBeTruthy();
    expect(screen.getByText('Dune + 1 more')).toBeTruthy();
    expect(listCall(f, 'role=incoming')).toBe(true);
    await fireEvent.press(screen.getByRole('tab', { name: 'Outgoing' }));
    await waitFor(() => expect(listCall(f, 'role=outgoing')).toBe(true));
  });

  it('opens on the section named in the route params', async () => {
    mockParams = { section: 'outgoing' };
    const f = await renderWith((m, p) => (p === '/api/proposals' ? { status: 200, body: page([]) } : undefined));
    await waitFor(() => expect(listCall(f, 'role=outgoing')).toBe(true));
    expect(screen.getByRole('tab', { name: 'Outgoing' })).toBeSelected();
  });

  it('Active shows two groups with status=ACCEPTED, one per role', async () => {
    const f = await renderWith((m, p) => (p === '/api/proposals' ? { status: 200, body: page([]) } : undefined));
    await fireEvent.press(screen.getByRole('tab', { name: 'Active' }));
    await waitFor(() => expect(listCall(f, 'role=incoming&status=ACCEPTED')).toBe(true));
    expect(listCall(f, 'role=outgoing&status=ACCEPTED')).toBe(true);
  });

  it('Active labels the groups when both have proposals', async () => {
    await renderWith((m, p) => {
      if (p !== '/api/proposals') return undefined;
      return { status: 200, body: page([proposal({ id: 'x', status: 'ACCEPTED', confirmBy: '2099-01-01T00:00:00Z' })]) };
    });
    await fireEvent.press(screen.getByRole('tab', { name: 'Active' }));
    expect(await screen.findByText('You received')).toBeTruthy();
    expect(screen.getByText('You proposed')).toBeTruthy();
  });

  it('History requests /api/exchanges and shows the other party and date', async () => {
    const f = await renderWith((m, p) => {
      if (p === '/api/proposals') return { status: 200, body: page([]) };
      if (p === '/api/exchanges') return { status: 200, body: page([{
        proposalId: 'p9', completedAt: '2026-10-05T12:00:00Z', with: party('u2', 'Deleted user', { city: null }),
        gave: [item('m1', 'Mine One')], received: [item('k1', 'Dune')],
      }]) };
    });
    await fireEvent.press(screen.getByRole('tab', { name: 'History' }));
    expect(await screen.findByText('Deleted user')).toBeTruthy();
    expect(screen.getByText(/Completed/)).toBeTruthy();
    expect(screen.getByText('Gave: Mine One')).toBeTruthy();
    expect(screen.getByText('Got: Dune')).toBeTruthy();
    expect(f.calls.some((c) => c.path.startsWith('/api/exchanges'))).toBe(true);
    await fireEvent.press(screen.getByRole('button', { name: 'Deleted user' }));
    expect(mockPush).toHaveBeenCalledWith({ pathname: '/exchanges/[id]', params: { id: 'p9' } });
  });

  it('an empty section shows its own empty text', async () => {
    await renderWith((m, p) => (p === '/api/proposals' ? { status: 200, body: page([]) } : undefined));
    expect(await screen.findByText('No proposals waiting for you')).toBeTruthy();
    await fireEvent.press(screen.getByRole('tab', { name: 'Outgoing' }));
    expect(await screen.findByText("You haven't proposed anything yet")).toBeTruthy();
  });

  it('a card with a null message and null city renders', async () => {
    await renderWith((m, p) => (p === '/api/proposals'
      ? { status: 200, body: page([proposal({ message: null, proposer: party('u2', 'Sam', { city: null }) })]) } : undefined));
    expect(await screen.findByText('Sam')).toBeTruthy();
  });

  it('a proposal past confirmBy is labelled "Settling soon"', async () => {
    await renderWith((m, p) => (p === '/api/proposals'
      ? { status: 200, body: page([proposal({ status: 'ACCEPTED', confirmBy: '2020-01-01T00:00:00Z' })]) } : undefined));
    expect(await screen.findByText('Settling soon')).toBeTruthy();
  });
});
