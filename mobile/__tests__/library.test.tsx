import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import LibraryScreen from '../app/(tabs)/library/index';
import CopyDetailScreen from '../app/(tabs)/library/[id]';
import { copyActions } from '../src/logic/copyActions';
import { apiError, copy, grades, page } from './fixtures';
import { Wrapper, fakeFetch, memoryTokens, Route } from './testUtils';

const mockPush = jest.fn();
const mockBack = jest.fn();
jest.mock('expo-router', () => ({
  Link: ({ children }: { children: React.ReactNode }) => {
    const { Text } = require('react-native');
    return <Text>{children}</Text>;
  },
  useRouter: () => ({ push: mockPush, back: mockBack, replace: jest.fn() }),
  useLocalSearchParams: () => ({ id: 'c1' }),
}));

const tokens = () => memoryTokens({ access: 'a', refresh: 'r' });

async function renderWith(ui: React.ReactElement, route: Route) {
  const f = fakeFetch((m, p, b) => (p === '/api/condition-grades' ? { status: 200, body: grades } : route(m, p, b)));
  await render(<Wrapper fetchImpl={f.impl} tokenStore={tokens()}>{ui}</Wrapper>);
  return f;
}

beforeEach(() => { mockPush.mockClear(); mockBack.mockClear(); });

describe('copyActions', () => {
  it('RESERVED copies offer nothing; UNLISTED can be listed; AVAILABLE can be unlisted', () => {
    expect(copyActions(copy({ status: 'RESERVED' }))).toEqual({ canChangeGrade: false, canList: false, canUnlist: false, canRemove: false });
    expect(copyActions(copy({ status: 'UNLISTED' }))).toEqual({ canChangeGrade: true, canList: true, canUnlist: false, canRemove: true });
    expect(copyActions(copy({ status: 'AVAILABLE' }))).toEqual({ canChangeGrade: true, canList: false, canUnlist: true, canRemove: true });
  });
});

describe('library grid', () => {
  it("shows each copy's title, author, grade and status", async () => {
    await renderWith(<LibraryScreen />, (m, p) =>
      p === '/api/copies' ? { status: 200, body: page([
        copy({ id: 'a', title: 'Dune', grade: 'GOOD', status: 'AVAILABLE' }),
        copy({ id: 'b', title: 'Emma', author: 'Jane Austen', grade: 'POOR', status: 'RESERVED' }),
      ]) } : undefined);
    expect(await screen.findByText('Dune')).toBeTruthy();
    expect(screen.getByText('Emma')).toBeTruthy();
    expect(screen.getByText('Jane Austen')).toBeTruthy();
    expect(screen.getByText('Poor')).toBeTruthy();
    expect(screen.getByText('Reserved')).toBeTruthy();
    expect(screen.getByText('Available')).toBeTruthy();
  });

  it('an empty library shows the empty state with an add prompt', async () => {
    await renderWith(<LibraryScreen />, (m, p) => (p === '/api/copies' ? { status: 200, body: page([]) } : undefined));
    expect(await screen.findByText('Your shelf is empty')).toBeTruthy();
    expect(screen.getByText('Tap + to add your first book.')).toBeTruthy();
  });

  it('tapping a book opens its details', async () => {
    await renderWith(<LibraryScreen />, (m, p) => (p === '/api/copies' ? { status: 200, body: page([copy({ id: 'a' })]) } : undefined));
    await fireEvent.press(await screen.findByRole('button', { name: 'Dune' }));
    expect(mockPush).toHaveBeenCalledWith({ pathname: '/library/[id]', params: { id: 'a' } });
  });
});

describe('copy details', () => {
  const get = (c: ReturnType<typeof copy>): Route => (m, p) => (m === 'GET' && p === '/api/copies/c1' ? { status: 200, body: c } : undefined);

  it('a RESERVED copy shows no change, list or remove controls', async () => {
    await renderWith(<CopyDetailScreen />, get(copy({ status: 'RESERVED' })));
    expect(await screen.findByText('Dune')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Unlist' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'List for swapping' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Remove' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Poor' })).toBeNull();
    expect(screen.getByText(/part of an accepted exchange/)).toBeTruthy();
  });

  it('an AVAILABLE copy offers Unlist, and Remove asks first then deletes', async () => {
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    const f = await renderWith(<CopyDetailScreen />, (m, p, b) => {
      if (m === 'GET' && p === '/api/copies/c1') return { status: 200, body: copy() };
      if (m === 'DELETE' && p === '/api/copies/c1') return { status: 204 };
    });
    expect(await screen.findByRole('button', { name: 'Unlist' })).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Remove' }));
    expect(alert).toHaveBeenCalledTimes(1);
    expect(f.calls.some((c) => c.method === 'DELETE')).toBe(false);
    const buttons = alert.mock.calls[0][2] as { text: string; onPress?: () => void }[];
    await buttons.find((b) => b.text === 'Remove')!.onPress!();
    await waitFor(() => expect(f.calls.some((c) => c.method === 'DELETE' && c.path === '/api/copies/c1')).toBe(true));
    await waitFor(() => expect(mockBack).toHaveBeenCalled());
    alert.mockRestore();
  });

  it('choosing another condition PUTs the new grade', async () => {
    const f = await renderWith(<CopyDetailScreen />, (m, p, b) => {
      if (m === 'GET' && p === '/api/copies/c1') return { status: 200, body: copy() };
      if (m === 'PUT' && p === '/api/copies/c1') return { status: 200, body: copy({ grade: 'POOR' }) };
    });
    await fireEvent.press(await screen.findByRole('button', { name: 'Poor' }));
    await waitFor(() => expect(f.calls.find((c) => c.method === 'PUT')?.body).toEqual({ grade: 'POOR' }));
  });

  it('PROFILE_INCOMPLETE on List shows the friendly message with a link to Profile', async () => {
    await renderWith(<CopyDetailScreen />, (m, p) => {
      if (m === 'GET' && p === '/api/copies/c1') return { status: 200, body: copy({ status: 'UNLISTED' }) };
      if (m === 'POST' && p === '/api/copies/c1/list') return apiError(409, 'PROFILE_INCOMPLETE');
    });
    await fireEvent.press(await screen.findByRole('button', { name: 'List for swapping' }));
    expect(await screen.findByText('Complete your profile (name, city and postal code) first.')).toBeTruthy();
    expect(screen.getByText('Go to Profile')).toBeTruthy();
  });
});
