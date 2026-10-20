import React from 'react';
import { fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react-native';
import ProfileScreen from '../app/(tabs)/profile/index';
import { useOpenProfileWhenIncomplete } from '../src/logic/useOpenProfileWhenIncomplete';
import { validateProfile } from '../src/logic/profileForm';
import { apiError } from './fixtures';
import { Wrapper, fakeFetch, memoryTokens, Route } from './testUtils';

const mockReplace = jest.fn();
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn(), replace: mockReplace }),
}));

const incomplete = { id: 'me', email: 'me@x.com', displayName: null, city: null, postalCode: null, contactNote: null, profileComplete: false };
const complete = { ...incomplete, displayName: 'Maya', city: 'Leeds', postalCode: 'LS1 4AB', contactNote: 'Evenings', profileComplete: true };

async function renderProfile(route: Route) {
  const tokens = memoryTokens({ access: 'a', refresh: 'r' });
  const f = fakeFetch(route);
  await render(<Wrapper fetchImpl={f.impl} tokenStore={tokens}><ProfileScreen /></Wrapper>);
  return { f, tokens };
}

const me = (body: unknown): Route => (m, p) => (m === 'GET' && p === '/api/users/me' ? { status: 200, body } : undefined);

describe('validateProfile', () => {
  it('requires name, city and postal code and enforces the backend limits (60/100/20/200)', () => {
    expect(validateProfile({ displayName: '', city: '', postalCode: '', contactNote: '' })).toEqual({
      displayName: 'Enter your name.', city: 'Enter your city.', postalCode: 'Enter your postal code.',
    });
    expect(validateProfile({ displayName: 'x'.repeat(61), city: 'y'.repeat(101), postalCode: 'z'.repeat(21), contactNote: 'n'.repeat(201) })).toEqual({
      displayName: 'Name can be at most 60 characters.', city: 'City can be at most 100 characters.',
      postalCode: 'Postal code can be at most 20 characters.', contactNote: 'Contact note can be at most 200 characters.',
    });
    expect(validateProfile({ displayName: 'a', city: 'b', postalCode: 'c', contactNote: '' })).toEqual({});
  });
});

describe('profile screen', () => {
  it('shows the profile and a banner when it is incomplete', async () => {
    await renderProfile(me(incomplete));
    expect(await screen.findByText(/Complete your profile/)).toBeTruthy();
    expect(screen.getByText('me@x.com')).toBeTruthy();
  });

  it('saving PUTs the four fields trimmed and the banner disappears', async () => {
    const { f } = await renderProfile((m, p) => {
      if (m === 'GET' && p === '/api/users/me') return { status: 200, body: incomplete };
      if (m === 'PUT' && p === '/api/users/me') return { status: 200, body: complete };
    });
    await fireEvent.changeText(await screen.findByLabelText('Display name'), '  Maya ');
    await fireEvent.changeText(screen.getByLabelText('City'), 'Leeds');
    await fireEvent.changeText(screen.getByLabelText('Postal code'), 'LS1 4AB');
    await fireEvent.changeText(screen.getByLabelText('Contact note (optional)'), ' Evenings ');
    await fireEvent.press(screen.getByRole('button', { name: 'Save profile' }));
    await waitFor(() => expect(f.calls.find((c) => c.method === 'PUT')?.body).toEqual({
      displayName: 'Maya', city: 'Leeds', postalCode: 'LS1 4AB', contactNote: 'Evenings',
    }));
    await waitFor(() => expect(screen.queryByText(/Complete your profile/)).toBeNull());
  });

  it('blocks an empty name and an over-long note without calling the API', async () => {
    const { f } = await renderProfile(me(incomplete));
    await fireEvent.changeText(await screen.findByLabelText('Contact note (optional)'), 'n'.repeat(201));
    await fireEvent.press(screen.getByRole('button', { name: 'Save profile' }));
    expect(await screen.findByText('Enter your name.')).toBeTruthy();
    expect(screen.getByText('Contact note can be at most 200 characters.')).toBeTruthy();
    expect(f.calls.some((c) => c.method === 'PUT')).toBe(false);
  });

  it('log out clears the session', async () => {
    const { tokens } = await renderProfile(me(complete));
    await fireEvent.press(await screen.findByRole('button', { name: 'Log out' }));
    await waitFor(() => expect(tokens.current).toBeNull());
  });
});

describe('delete account', () => {
  const del = (res: { status: number; body?: unknown }): Route => (m, p) => {
    if (m === 'GET' && p === '/api/users/me') return { status: 200, body: complete };
    if (m === 'DELETE' && p === '/api/users/me') return res;
  };

  async function openDelete(password: string) {
    await fireEvent.press(await screen.findByRole('button', { name: 'Delete account' }));
    await fireEvent.changeText(screen.getByLabelText('Current password'), password);
    await fireEvent.press(screen.getByRole('button', { name: 'Delete my account' }));
  }

  it('asks for the password, sends it, and signs out on 204', async () => {
    const { f, tokens } = await renderProfile(del({ status: 204 }));
    await openDelete('password123');
    await waitFor(() => expect(f.calls.find((c) => c.method === 'DELETE')?.body).toEqual({ password: 'password123' }));
    await waitFor(() => expect(tokens.current).toBeNull());
  });

  it('a wrong password shows the message and does not sign out', async () => {
    const { tokens } = await renderProfile(del(apiError(401, 'INVALID_CREDENTIALS')));
    await openDelete('nope-nope');
    expect(await screen.findByText('Wrong email or password.')).toBeTruthy();
    expect(tokens.current).not.toBeNull();
  });

  it('ACCOUNT_HAS_ACTIVE_EXCHANGE explains and does not sign out', async () => {
    const { tokens } = await renderProfile(del(apiError(409, 'ACCOUNT_HAS_ACTIVE_EXCHANGE')));
    await openDelete('password123');
    expect(await screen.findByText('Finish or cancel your accepted exchanges first.')).toBeTruthy();
    expect(tokens.current).not.toBeNull();
  });
});

describe('useOpenProfileWhenIncomplete', () => {
  beforeEach(() => mockReplace.mockClear());

  function setup(body: unknown) {
    const f = fakeFetch(me(body));
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <Wrapper fetchImpl={f.impl} tokenStore={memoryTokens({ access: 'a', refresh: 'r' })}>{children}</Wrapper>
    );
    return renderHook(() => useOpenProfileWhenIncomplete(), { wrapper });
  }

  it('sends a new user with an incomplete profile to the Profile tab', async () => {
    await setup(incomplete);
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/profile'));
  });

  it('leaves a complete profile where it is', async () => {
    await setup(complete);
    await new Promise((r) => setTimeout(r, 50));
    expect(mockReplace).not.toHaveBeenCalled();
  });
});
