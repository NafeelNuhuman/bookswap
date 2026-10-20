import React from 'react';
import { Text } from 'react-native';
import { render, screen } from '@testing-library/react-native';
import { Screen } from '../src/components/Screen';

jest.mock('react-native/Libraries/Components/Keyboard/KeyboardAvoidingView', () => {
  const { Text: T } = require('react-native');
  return { __esModule: true, default: ({ behavior, children }: { behavior?: string; children: React.ReactNode }) => (
    <>
      <T>{`KAV:${behavior}`}</T>
      {children}
    </>
  ) };
});

describe('Screen', () => {
  it('keeps its content above the on-screen keyboard (edge-to-edge Android does not resize for it)', async () => {
    await render(<Screen scroll><Text>form</Text></Screen>);
    expect(screen.getByText('KAV:padding')).toBeTruthy();
    expect(screen.getByText('form')).toBeTruthy();
  });
});
