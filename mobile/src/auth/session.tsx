import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { Text, View } from 'react-native';
import { useQueryClient } from '@tanstack/react-query';
import { ApiClient, TokenStore, createApiClient } from '../api/client';
import type { TokenResponse } from '../api/types';
import { theme } from '../theme';
import { secureTokenStore } from './tokenStore';

type Status = 'loading' | 'signedOut' | 'signedIn';

type SessionValue = {
  status: Status;
  signIn(email: string, password: string): Promise<void>;
  register(email: string, password: string): Promise<void>;
  signOut(): Promise<void>;
  api: ApiClient;
};

const SessionContext = createContext<SessionValue | null>(null);

type Props = {
  children: React.ReactNode;
  tokenStore?: TokenStore;
  baseUrl?: string;
  fetchImpl?: typeof fetch;
};

export function SessionProvider({
  children,
  tokenStore = secureTokenStore,
  baseUrl = process.env.EXPO_PUBLIC_API_URL,
  fetchImpl,
}: Props) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<Status>('loading');

  const api = useMemo(
    () =>
      createApiClient({
        baseUrl: baseUrl ?? '',
        tokens: tokenStore,
        fetchImpl,
        onSignedOut: () => {
          queryClient.clear();
          setStatus('signedOut');
        },
      }),
    [baseUrl, tokenStore, fetchImpl, queryClient],
  );

  useEffect(() => {
    let cancelled = false;
    tokenStore.get().then((t) => {
      if (!cancelled) setStatus(t ? 'signedIn' : 'signedOut');
    });
    return () => {
      cancelled = true;
    };
  }, [tokenStore]);

  const signIn = useCallback(
    async (email: string, password: string) => {
      const t = await api.request<TokenResponse>('POST', '/api/auth/login', { body: { email, password }, auth: false });
      await tokenStore.set({ access: t.accessToken, refresh: t.refreshToken });
      setStatus('signedIn');
    },
    [api, tokenStore],
  );

  const register = useCallback(
    async (email: string, password: string) => {
      await api.request('POST', '/api/auth/register', { body: { email, password }, auth: false });
      await signIn(email, password);
    },
    [api, signIn],
  );

  const signOut = useCallback(async () => {
    await tokenStore.clear();
    queryClient.clear();
    setStatus('signedOut');
  }, [tokenStore, queryClient]);

  const value = useMemo(() => ({ status, signIn, register, signOut, api }), [status, signIn, register, signOut, api]);

  if (!baseUrl) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', padding: 24, backgroundColor: theme.colors.cream }}>
        <Text style={{ fontFamily: theme.fonts.title, fontSize: 22, color: theme.colors.brown }}>
          API address not set
        </Text>
        <Text style={{ marginTop: 8, color: theme.colors.ink }}>
          Set EXPO_PUBLIC_API_URL (see mobile/README.md) and restart Expo.
        </Text>
      </View>
    );
  }

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error('useSession must be used inside SessionProvider');
  return ctx;
}

export function useApi(): ApiClient {
  return useSession().api;
}
