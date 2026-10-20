import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import AddBookScreen from '../app/(tabs)/library/add';
import { apiError, copy, grades } from './fixtures';
import { Wrapper, fakeFetch, memoryTokens, Route } from './testUtils';

const mockBack = jest.fn();
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), back: mockBack, replace: jest.fn() }),
}));

let mockPermission: { granted: boolean; canAskAgain: boolean } = { granted: true, canAskAgain: true };
let mockScan: ((r: { data: string; type: string }) => void) | undefined;
jest.mock('expo-camera', () => {
  const { View } = require('react-native');
  return {
    CameraView: (props: { onBarcodeScanned?: (r: { data: string; type: string }) => void }) => {
      mockScan = props.onBarcodeScanned;
      return <View testID="camera" />;
    },
    useCameraPermissions: () => [mockPermission, jest.fn()],
  };
});

const dune = { isbn: '9780441013593', title: 'Dune', author: 'Frank Herbert', coverUrl: 'http://x/dune.jpg', source: 'CATALOG' };

async function renderAdd(route: Route) {
  const f = fakeFetch((m, p, b) => (p === '/api/condition-grades' ? { status: 200, body: grades } : route(m, p, b)));
  await render(<Wrapper fetchImpl={f.impl} tokenStore={memoryTokens({ access: 'a', refresh: 'r' })}><AddBookScreen /></Wrapper>);
  return f;
}

const found: Route = (m, p) => {
  if (p === '/api/books/lookup') return { status: 200, body: dune };
  if (m === 'POST' && p === '/api/copies') return { status: 201, body: copy() };
};

async function typeIsbnAndLookUp(value: string) {
  await fireEvent.changeText(await screen.findByLabelText('ISBN'), value);
  await fireEvent.press(screen.getByRole('button', { name: 'Look up' }));
}

beforeEach(() => {
  mockBack.mockClear();
  mockPermission = { granted: true, canAskAgain: true };
  mockScan = undefined;
});

describe('adding a book', () => {
  it('typing a valid ISBN shows the found book, then saves it with the chosen grade', async () => {
    const f = await renderAdd(found);
    await typeIsbnAndLookUp('978-0-441-01359-3');
    expect(await screen.findByText('Dune')).toBeTruthy();
    expect(screen.getByText('Frank Herbert')).toBeTruthy();
    expect(f.calls.find((c) => c.path.startsWith('/api/books/lookup'))?.path).toContain('isbn=9780441013593');
    await fireEvent.press(screen.getByRole('button', { name: 'Poor' }));
    await fireEvent.press(screen.getByRole('button', { name: 'Add to my library' }));
    await waitFor(() => expect(f.calls.find((c) => c.method === 'POST')?.body).toEqual({ isbn: '9780441013593', grade: 'POOR' }));
    await waitFor(() => expect(mockBack).toHaveBeenCalled());
  });

  it('BOOK_NOT_FOUND reveals title and author fields and the POST includes them', async () => {
    const f = await renderAdd((m, p) => {
      if (p === '/api/books/lookup') return apiError(404, 'BOOK_NOT_FOUND');
      if (m === 'POST' && p === '/api/copies') return { status: 201, body: copy() };
    });
    await typeIsbnAndLookUp('9780441013593');
    await fireEvent.changeText(await screen.findByLabelText('Title'), 'My Rare Book');
    await fireEvent.changeText(screen.getByLabelText('Author'), 'A. Writer');
    await fireEvent.press(screen.getByRole('button', { name: 'Add to my library' }));
    await waitFor(() =>
      expect(f.calls.find((c) => c.method === 'POST')?.body).toEqual({ isbn: '9780441013593', grade: 'GOOD', title: 'My Rare Book', author: 'A. Writer' }));
  });

  it('an invalid ISBN shows an error without calling the lookup', async () => {
    const f = await renderAdd(found);
    await typeIsbnAndLookUp('1234');
    expect(await screen.findByText("That isn't a valid ISBN.")).toBeTruthy();
    expect(f.calls.some((c) => c.path.startsWith('/api/books/lookup'))).toBe(false);
  });
});

describe('scanning', () => {
  it("a scanned non-ISBN barcode says so and keeps scanning", async () => {
    const f = await renderAdd(found);
    await fireEvent.press(await screen.findByRole('button', { name: 'Scan barcode' }));
    expect(screen.getByTestId('camera')).toBeTruthy();
    await act(async () => { mockScan?.({ data: '012345678905', type: 'ean13' }); });
    expect(await screen.findByText("That doesn't look like a book barcode.")).toBeTruthy();
    expect(screen.getByTestId('camera')).toBeTruthy();
    expect(f.calls.some((c) => c.path.startsWith('/api/books/lookup'))).toBe(false);
  });

  it('a scanned ISBN closes the camera and looks the book up', async () => {
    const f = await renderAdd(found);
    await fireEvent.press(await screen.findByRole('button', { name: 'Scan barcode' }));
    await act(async () => { mockScan?.({ data: '9780441013593', type: 'ean13' }); });
    expect(await screen.findByText('Dune')).toBeTruthy();
    expect(screen.queryByTestId('camera')).toBeNull();
    expect(f.calls.some((c) => c.path.startsWith('/api/books/lookup'))).toBe(true);
  });

  it('denied camera permission explains it and typing still works', async () => {
    mockPermission = { granted: false, canAskAgain: false };
    await renderAdd(found);
    await fireEvent.press(await screen.findByRole('button', { name: 'Scan barcode' }));
    expect(await screen.findByText(/Camera access is off/)).toBeTruthy();
    await typeIsbnAndLookUp('9780441013593');
    expect(await screen.findByText('Dune')).toBeTruthy();
  });
});
