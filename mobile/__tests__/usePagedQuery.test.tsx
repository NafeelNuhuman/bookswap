import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react-native';
import { usePagedQuery } from '../src/api/usePagedQuery';
import type { Page } from '../src/api/types';

const wrapper = ({ children }: { children: React.ReactNode }) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
};

const page = (n: number, totalPages: number, content: number[]): Page<number> => ({
  content, page: n, size: 2, totalElements: 4, totalPages,
});

describe('usePagedQuery', () => {
  it('flattens pages and hasNextPage is false when page+1 >= totalPages', async () => {
    const fetchPage = jest.fn(async (n: number) => (n === 0 ? page(0, 2, [1, 2]) : page(1, 2, [3, 4])));
    const { result } = await renderHook(() => usePagedQuery(['nums'], fetchPage), { wrapper });
    await waitFor(() => expect(result.current.items).toEqual([1, 2]));
    expect(result.current.hasNextPage).toBe(true);
    await act(async () => { await result.current.fetchNextPage(); });
    await waitFor(() => expect(result.current.items).toEqual([1, 2, 3, 4]));
    expect(result.current.hasNextPage).toBe(false);
    expect(fetchPage).toHaveBeenCalledTimes(2);
  });

  it('a single page has no next page and an empty result is an empty list', async () => {
    const { result } = await renderHook(() => usePagedQuery(['empty'], async () => page(0, 0, [])), { wrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.items).toEqual([]);
    expect(result.current.hasNextPage).toBe(false);
  });
});
