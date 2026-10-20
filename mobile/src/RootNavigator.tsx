import React from 'react';
import { Stack } from 'expo-router';
import { useSession } from './auth/session';
import { stackOptions } from './stackOptions';

/** Signed-in users get the tabs and the compose modal; signed-out users get login and register. */
export function RootNavigator() {
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
