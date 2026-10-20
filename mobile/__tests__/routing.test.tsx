import React from 'react';
import { Text } from 'react-native';
import { Stack, Tabs } from 'expo-router';
import { renderRouter, screen } from 'expo-router/testing-library';
import Index from '../app/index';
import { RootNavigator } from '../src/RootNavigator';

let mockStatus: 'loading' | 'signedOut' | 'signedIn' = 'signedOut';
jest.mock('../src/auth/session', () => ({ useSession: () => ({ status: mockStatus }) }));

const stub = (label: string) => () => <Text>{label}</Text>;
const stackLayout = () => <Stack />;
const tabsLayout = () => (
  <Tabs>
    <Tabs.Screen name="library" />
    <Tabs.Screen name="discover" />
    <Tabs.Screen name="exchanges" />
    <Tabs.Screen name="profile" />
  </Tabs>
);

// The real root navigator (with its sign-in guards) and the real index route over stub screens.
const routes = {
  _layout: RootNavigator,
  index: Index,
  compose: stub('COMPOSE'),
  '(auth)/_layout': stackLayout,
  '(auth)/login': stub('LOGIN'),
  '(auth)/register': stub('REGISTER'),
  '(tabs)/_layout': tabsLayout,
  '(tabs)/library/_layout': stackLayout,
  '(tabs)/library/index': stub('LIBRARY'),
  '(tabs)/discover/_layout': stackLayout,
  '(tabs)/discover/index': stub('DISCOVER'),
  '(tabs)/exchanges/_layout': stackLayout,
  '(tabs)/exchanges/index': stub('EXCHANGES'),
  '(tabs)/profile/_layout': stackLayout,
  '(tabs)/profile/index': stub('PROFILE'),
};

describe('launch routing', () => {
  it('a signed-out launch at / lands on the login screen', async () => {
    mockStatus = 'signedOut';
    await renderRouter(routes, { initialUrl: '/' });
    expect(await screen.findByText('LOGIN')).toBeTruthy();
  });

  it('a signed-in launch at / lands on the library', async () => {
    mockStatus = 'signedIn';
    const r = await renderRouter(routes, { initialUrl: '/' });
    expect(await screen.findByText('LIBRARY')).toBeTruthy();
    expect(r.getPathname()).toBe('/library');
  });
});
