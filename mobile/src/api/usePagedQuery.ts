import { useInfiniteQuery } from '@tanstack/react-query';
import type { Page } from './types';

/** Infinite scroll over a paginated endpoint; `items` is every loaded page flattened. */
export function usePagedQuery<T>(key: unknown[], fetchPage: (page: number) => Promise<Page<T>>) {
  const q = useInfiniteQuery({
    queryKey: key,
    queryFn: ({ pageParam }) => fetchPage(pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
  return {
    items: q.data?.pages.flatMap((p) => p.content) ?? [],
    fetchNextPage: q.fetchNextPage,
    hasNextPage: q.hasNextPage,
    isLoading: q.isLoading,
    isFetchingNextPage: q.isFetchingNextPage,
    isError: q.isError,
    error: q.error,
    refetch: q.refetch,
    isRefetching: q.isRefetching,
  };
}
