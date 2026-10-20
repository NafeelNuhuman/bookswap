import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import ComposeScreen from '../app/compose';
import { apiError, card, copy, item, page, proposal } from './fixtures';
import { Wrapper, fakeFetch, memoryTokens, Route } from './testUtils';

const mockDismissTo = jest.fn();
let mockParams: Record<string, string> = {};
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn(), replace: jest.fn(), dismissTo: mockDismissTo }),
  useLocalSearchParams: () => mockParams,
}));
jest.mock('../src/components/GradePicker', () => ({ GradePicker: () => null }));

const theirShelf = page([card({ copyId: 'k1', title: 'Dune' }), card({ copyId: 'k2', title: 'Emma' })]);
const myShelf = page([copy({ id: 'm1', title: 'Mine One', status: 'AVAILABLE' }), copy({ id: 'm2', title: 'Mine Two' })]);

function baseRoute(extra: Route = () => undefined): Route {
  return (m, p, b) => {
    if (m === 'GET' && p === '/api/users/u2/copies') return { status: 200, body: theirShelf };
    if (m === 'GET' && p === '/api/copies') return { status: 200, body: myShelf };
    return extra(m, p, b);
  };
}

async function renderCompose(route: Route) {
  const f = fakeFetch(route);
  await render(<Wrapper fetchImpl={f.impl} tokenStore={memoryTokens({ access: 'a', refresh: 'r' })}><ComposeScreen /></Wrapper>);
  return f;
}

const row = (name: string) => screen.findByRole('checkbox', { name });

beforeEach(() => { mockDismissTo.mockClear(); mockParams = { recipientId: 'u2', wantedCopyId: 'k1' }; });

describe('compose screen: new proposal', () => {
  it("opens with the tapped book selected and submit disabled until one of mine is ticked", async () => {
    await renderCompose(baseRoute());
    expect(await row('Dune')).toBeChecked();
    expect(await row('Emma')).not.toBeChecked();
    expect(screen.getByRole('button', { name: 'Send proposal' })).toBeDisabled();
    await fireEvent.press(await row('Mine One'));
    expect(screen.getByRole('button', { name: 'Send proposal' })).toBeEnabled();
  });

  it('my side asks only for my AVAILABLE copies', async () => {
    const f = await renderCompose(baseRoute());
    await row('Mine One');
    expect(f.calls.find((c) => c.path.startsWith('/api/copies'))?.path).toContain('status=AVAILABLE');
  });

  it('submit POSTs both id lists and the message, then opens Outgoing', async () => {
    const f = await renderCompose(baseRoute((m, p) => (m === 'POST' && p === '/api/proposals' ? { status: 201, body: proposal() } : undefined)));
    await fireEvent.press(await row('Emma'));
    await fireEvent.press(await row('Mine One'));
    await fireEvent.changeText(screen.getByLabelText('Message (optional)'), 'Hello there');
    await fireEvent.press(screen.getByRole('button', { name: 'Send proposal' }));
    await waitFor(() => expect(f.calls.find((c) => c.method === 'POST')?.body).toEqual({
      requestedCopyIds: ['k1', 'k2'], offeredCopyIds: ['m1'], message: 'Hello there',
    }));
    await waitFor(() => expect(mockDismissTo).toHaveBeenCalledWith({ pathname: '/exchanges', params: { section: 'outgoing' } }));
  });

  it('COPY_NOT_AVAILABLE shows the friendly message and keeps the selection', async () => {
    await renderCompose(baseRoute((m, p) => (m === 'POST' ? apiError(409, 'COPY_NOT_AVAILABLE') : undefined)));
    await fireEvent.press(await row('Mine One'));
    await fireEvent.press(screen.getByRole('button', { name: 'Send proposal' }));
    expect(await screen.findByText('One of the books is no longer available.')).toBeTruthy();
    expect(await row('Mine One')).toBeChecked();
    expect(await row('Dune')).toBeChecked();
    expect(mockDismissTo).not.toHaveBeenCalled();
  });

  it('the message box is limited to 500 characters', async () => {
    await renderCompose(baseRoute());
    await row('Dune');
    expect(screen.getByLabelText('Message (optional)').props.maxLength).toBe(500);
  });
});

describe('compose screen: counter-offer', () => {
  it('is pre-filled from the proposal and POSTs the counter', async () => {
    mockParams = { counterOf: 'p1' };
    const f = await renderCompose(baseRoute((m, p) => {
      if (m === 'GET' && p === '/api/proposals/p1') return { status: 200, body: proposal({ offered: [item('k1', 'Dune')], requested: [item('m1', 'Mine One')] }) };
      if (m === 'POST' && p === '/api/proposals/p1/counter') return { status: 201, body: proposal({ id: 'p2' }) };
    }));
    expect(await row('Dune')).toBeChecked();
    expect(await row('Mine One')).toBeChecked();
    expect(await row('Mine Two')).not.toBeChecked();
    await fireEvent.press(await row('Mine Two'));
    await fireEvent.press(screen.getByRole('button', { name: 'Send counter-offer' }));
    await waitFor(() => expect(f.calls.find((c) => c.path === '/api/proposals/p1/counter')?.body).toEqual({
      requestedCopyIds: ['k1'], offeredCopyIds: ['m1', 'm2'], message: null,
    }));
    await waitFor(() => expect(mockDismissTo).toHaveBeenCalledWith({ pathname: '/exchanges', params: { section: 'outgoing' } }));
  });
});
