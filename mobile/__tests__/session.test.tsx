import React from 'react';
import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useSession } from '../src/auth/session';
import { Wrapper, fakeFetch, memoryTokens, tokenResponse } from './testUtils';

function setup(route: Parameters<typeof fakeFetch>[0], initial: Parameters<typeof memoryTokens>[0] = null) {
  const tokens = memoryTokens(initial);
  const f = fakeFetch(route);
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <Wrapper fetchImpl={f.impl} tokenStore={tokens}>{children}</Wrapper>
  );
  return { tokens, f, wrapper };
}

describe('session', () => {
  it('signIn stores the tokens and status becomes signedIn', async () => {
    const { tokens, wrapper } = setup((m, p) => (p === '/api/auth/login' ? { status: 200, body: tokenResponse } : undefined));
    const { result } = await renderHook(() => useSession(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('signedOut'));
    await act(async () => { await result.current.signIn('a@b.c', 'password123'); });
    expect(result.current.status).toBe('signedIn');
    expect(tokens.current).toEqual({ access: 'acc', refresh: 'ref' });
  });

  it('register then signs in with the same credentials', async () => {
    const { f, wrapper } = setup((m, p) => {
      if (p === '/api/auth/register') return { status: 201, body: { id: 'u1' } };
      if (p === '/api/auth/login') return { status: 200, body: tokenResponse };
    });
    const { result } = await renderHook(() => useSession(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('signedOut'));
    await act(async () => { await result.current.register('a@b.c', 'password123'); });
    expect(f.calls.map((c) => c.path)).toEqual(['/api/auth/register', '/api/auth/login']);
    expect(f.calls[1].body).toEqual({ email: 'a@b.c', password: 'password123' });
    expect(result.current.status).toBe('signedIn');
  });

  it('signOut clears the tokens and status becomes signedOut', async () => {
    const { tokens, wrapper } = setup(() => undefined, { access: 'x', refresh: 'y' });
    const { result } = await renderHook(() => useSession(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('signedIn'));
    await act(async () => { await result.current.signOut(); });
    expect(result.current.status).toBe('signedOut');
    expect(tokens.current).toBeNull();
  });

  it('a stored token pair at start gives signedIn without any request', async () => {
    const { f, wrapper } = setup(() => undefined, { access: 'x', refresh: 'y' });
    const { result } = await renderHook(() => useSession(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('signedIn'));
    expect(f.calls).toHaveLength(0);
  });

  it('the client signing out (refresh refused) flips the session to signedOut', async () => {
    const { wrapper } = setup((m, p) =>
      p === '/api/auth/refresh'
        ? { status: 401, body: { status: 401, code: 'INVALID_REFRESH_TOKEN', message: 'no' } }
        : { status: 401, body: { status: 401, code: 'UNAUTHORIZED', message: 'no' } },
    { access: 'x', refresh: 'y' });
    const { result } = await renderHook(() => useSession(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('signedIn'));
    await act(async () => { await result.current.api.request('GET', '/api/copies').catch(() => undefined); });
    expect(result.current.status).toBe('signedOut');
  });
});
