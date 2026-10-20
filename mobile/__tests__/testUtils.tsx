import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SessionProvider } from '../src/auth/session';
import type { TokenStore } from '../src/api/client';

export type FakeResponse = { status: number; body?: unknown };
export type Route = (method: string, path: string, body: unknown) => FakeResponse | undefined;

export function memoryTokens(initial: { access: string; refresh: string } | null = null) {
  const store: TokenStore & { current: { access: string; refresh: string } | null } = {
    current: initial,
    get: async () => store.current,
    set: async (t) => { store.current = t; },
    clear: async () => { store.current = null; },
  };
  return store;
}

/** A fetch that answers from a route function and records every request. */
export function fakeFetch(route: Route) {
  const calls: { method: string; path: string; body: unknown }[] = [];
  const impl = jest.fn(async (url: string, init: RequestInit) => {
    const path = url.replace('http://test', '');
    const body = init.body ? JSON.parse(init.body as string) : undefined;
    const method = init.method ?? 'GET';
    calls.push({ method, path, body });
    const r = route(method, path.split('?')[0], body) ?? { status: 404, body: { code: 'NOT_MOCKED', message: path } };
    return {
      status: r.status,
      ok: r.status < 400,
      text: async () => (r.body === undefined ? '' : JSON.stringify(r.body)),
    } as Response;
  });
  return { impl: impl as unknown as typeof fetch, calls };
}

export const tokenResponse = { accessToken: 'acc', refreshToken: 'ref', tokenType: 'Bearer', expiresInSeconds: 900 };

export function Wrapper({ children, fetchImpl, tokenStore }: { children: React.ReactNode; fetchImpl: typeof fetch; tokenStore: TokenStore }) {
  const client = React.useMemo(() => new QueryClient({ defaultOptions: { queries: { retry: false } } }), []);
  return (
    <QueryClientProvider client={client}>
      <SessionProvider baseUrl="http://test" fetchImpl={fetchImpl} tokenStore={tokenStore}>
        {children}
      </SessionProvider>
    </QueryClientProvider>
  );
}
