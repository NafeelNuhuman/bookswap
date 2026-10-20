import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import DiscoverScreen from '../app/(tabs)/discover/index';
import DiscoverBookScreen from '../app/(tabs)/discover/book';
import { apiError, card, page } from './fixtures';
import { Wrapper, fakeFetch, memoryTokens, Route } from './testUtils';

const mockPush = jest.fn();
let mockParams: Record<string, string> = {};
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn(), replace: jest.fn() }),
  useLocalSearchParams: () => mockParams,
}));

async function renderWith(ui: React.ReactElement, route: Route) {
  const f = fakeFetch(route);
  await render(<Wrapper fetchImpl={f.impl} tokenStore={memoryTokens({ access: 'a', refresh: 'r' })}>{ui}</Wrapper>);
  return f;
}

beforeEach(() => { mockPush.mockClear(); mockParams = {}; });

describe('discover list', () => {
  it('with an empty search box the feed is shown, with owner name and city under each book', async () => {
    const f = await renderWith(<DiscoverScreen />, (m, p) => (p === '/api/feed' ? { status: 200, body: page([
      card({ copyId: 'k1', title: 'Dune' }),
      card({ copyId: 'k2', title: 'Emma', owner: { id: 'u3', displayName: 'Alex', city: null } }),
    ]) } : undefined));
    expect(await screen.findByText('Dune')).toBeTruthy();
    expect(screen.getByText('Sam · Leeds')).toBeTruthy();
    expect(screen.getByText('Alex')).toBeTruthy();
    expect(f.calls.some((c) => c.path.startsWith('/api/search'))).toBe(false);
  });

  it('typing queries /api/search with q after the debounce and not before', async () => {
    const f = await renderWith(<DiscoverScreen />, (m, p) => {
      if (p === '/api/feed') return { status: 200, body: page([card()]) };
      if (p === '/api/search') return { status: 200, body: page([card({ copyId: 'k9', title: 'Emma' })]) };
    });
    await screen.findByText('Dune');
    await fireEvent.changeText(screen.getByLabelText('Search by title, author or ISBN'), '  emma ');
    expect(f.calls.some((c) => c.path.startsWith('/api/search'))).toBe(false);
    expect(await screen.findByText('Emma')).toBeTruthy();
    expect(f.calls.find((c) => c.path.startsWith('/api/search'))?.path).toContain('q=emma');
  });

  it('no results shows "Nothing here yet"', async () => {
    await renderWith(<DiscoverScreen />, (m, p) => (p === '/api/feed' ? { status: 200, body: page([]) } : undefined));
    expect(await screen.findByText('Nothing here yet')).toBeTruthy();
  });

  it('INVALID_QUERY shows a friendly message', async () => {
    await renderWith(<DiscoverScreen />, (m, p) => {
      if (p === '/api/feed') return { status: 200, body: page([card()]) };
      if (p === '/api/search') return apiError(400, 'INVALID_QUERY');
    });
    await screen.findByText('Dune');
    await fireEvent.changeText(screen.getByLabelText('Search by title, author or ISBN'), 'x'.repeat(150));
    await waitFor(() => expect(screen.getByText('Search text must be 1 to 100 characters.')).toBeTruthy());
  });

  it('tapping a book opens its details with the card as a param', async () => {
    const c = card();
    await renderWith(<DiscoverScreen />, (m, p) => (p === '/api/feed' ? { status: 200, body: page([c]) } : undefined));
    await fireEvent.press(await screen.findByRole('button', { name: 'Dune' }));
    expect(mockPush).toHaveBeenCalledWith({ pathname: '/discover/book', params: { card: JSON.stringify(c) } });
  });
});

describe('discover book details', () => {
  it('shows the book, its owner, and Propose swap navigates with recipientId and wantedCopyId', async () => {
    mockParams = { card: JSON.stringify(card({ copyId: 'k1', owner: { id: 'u2', displayName: 'Sam', city: null } })) };
    await renderWith(<DiscoverBookScreen />, () => undefined);
    expect(screen.getByText('Dune')).toBeTruthy();
    expect(screen.getByText('Frank Herbert')).toBeTruthy();
    expect(screen.getByText('Good')).toBeTruthy();
    expect(screen.getByText('Sam')).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Propose swap' }));
    expect(mockPush).toHaveBeenCalledWith({ pathname: '/compose', params: { recipientId: 'u2', wantedCopyId: 'k1' } });
  });
});
