import React, { useState } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { createQueryClient } from '../src/api/queryClient';
import { SessionProvider, useSession } from '../src/auth/session';
import { stackOptions } from '../src/stackOptions';

function RootNavigator() {
  const { status } = useSession();
  if (status === 'loading') return null;
  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Protected guard={status === 'signedIn'}>
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="compose" options={{ presentation: 'modal', headerShown: true, title: 'Propose a swap', ...stackOptions }} />
      </Stack.Protected>
      <Stack.Protected guard={status === 'signedOut'}>
        <Stack.Screen name="(auth)" />
      </Stack.Protected>
    </Stack>
  );
}

export default function RootLayout() {
  const [queryClient] = useState(createQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <StatusBar style="dark" />
        <RootNavigator />
      </SessionProvider>
    </QueryClientProvider>
  );
}
