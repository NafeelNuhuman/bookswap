import React from 'react';
import { useRouter } from 'expo-router';
import { listCopies } from '../../../src/api/endpoints/copies';
import { usePagedQuery } from '../../../src/api/usePagedQuery';
import type { Copy } from '../../../src/api/types';
import { useApi } from '../../../src/auth/session';
import { BookCard } from '../../../src/components/BookCard';
import { BookGrid } from '../../../src/components/BookGrid';

export default function LibraryScreen() {
  const api = useApi();
  const router = useRouter();
  const q = usePagedQuery<Copy>(['copies', 'list'], (page) => listCopies(api, { page }));
  return (
    <BookGrid
      items={q.items}
      keyOf={(c) => c.id}
      renderItem={(c) => (
        <BookCard
          title={c.title}
          author={c.author}
          coverUrl={c.coverUrl}
          grade={c.grade}
          status={c.status}
          onPress={() => router.push({ pathname: '/library/[id]', params: { id: c.id } })}
        />
      )}
      emptyText="Your shelf is empty"
      emptyHint="Tap + to add your first book."
      isLoading={q.isLoading}
      isError={q.isError}
      error={q.error}
      hasNextPage={!!q.hasNextPage}
      onEndReached={() => q.fetchNextPage()}
      onRetry={() => q.refetch()}
      onRefresh={() => q.refetch()}
      refreshing={q.isRefetching}
    />
  );
}
