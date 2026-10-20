import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import LoginScreen from '../app/(auth)/login';
import { Wrapper, fakeFetch, memoryTokens } from './testUtils';

jest.mock('expo-router', () => ({
  Link: ({ children }: { children: React.ReactNode }) => {
    const { Text } = require("react-native");
    return <Text>{children}</Text>;
  },
  useRouter: () => ({ push: jest.fn(), replace: jest.fn() }),
}));

async function renderLogin(route: Parameters<typeof fakeFetch>[0]) {
  const f = fakeFetch(route);
  await render(<Wrapper fetchImpl={f.impl} tokenStore={memoryTokens()}><LoginScreen /></Wrapper>);
  return f;
}

describe('login screen', () => {
  it('a wrong password shows the friendly message and stays on the screen', async () => {
    await renderLogin(() => ({ status: 401, body: { status: 401, code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' } }));
    await fireEvent.changeText(await screen.findByLabelText('Email'), 'a@b.c');
    await fireEvent.changeText(screen.getByLabelText('Password'), 'wrongwrong');
    await fireEvent.press(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Wrong email or password.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeTruthy();
  });

  it('the submit button is disabled while the request is pending', async () => {
    let release: () => void = () => undefined;
    const gate = new Promise<void>((r) => { release = r; });
    const f = fakeFetch(() => ({ status: 200, body: { accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer', expiresInSeconds: 1 } }));
    const slow = (async (...args: Parameters<typeof fetch>) => { await gate; return f.impl(...args); }) as typeof fetch;
    await render(<Wrapper fetchImpl={slow} tokenStore={memoryTokens()}><LoginScreen /></Wrapper>);
    await fireEvent.changeText(await screen.findByLabelText('Email'), 'a@b.c');
    await fireEvent.changeText(screen.getByLabelText('Password'), 'password123');
    void fireEvent.press(screen.getByRole('button', { name: 'Sign in' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Sign in' })).toBeDisabled());
    release();
  });
});
