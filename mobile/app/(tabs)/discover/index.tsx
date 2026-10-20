import React, { useState } from 'react';
import { View } from 'react-native';
import { useRouter } from 'expo-router';
import { feed, search } from '../../../src/api/endpoints/discovery';
import { usePagedQuery } from '../../../src/api/usePagedQuery';
import type { CopyCard } from '../../../src/api/types';
import { useApi } from '../../../src/auth/session';
import { BookCard } from '../../../src/components/BookCard';
import { BookGrid } from '../../../src/components/BookGrid';
import { TextField } from '../../../src/components/TextField';
import { ownerLine } from '../../../src/logic/format';
import { useDebounced } from '../../../src/logic/useDebounced';
import { theme } from '../../../src/theme';

export default function DiscoverScreen() {
  const api = useApi();
  const router = useRouter();
  const [text, setText] = useState('');
  const q = useDebounced(text.trim(), 400);

  const results = usePagedQuery<CopyCard>(['discover', q ? 'search' : 'feed', q], (page) =>
    q ? search(api, q, page) : feed(api, page));

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.cream }}>
      <View style={{ paddingHorizontal: 14 }}>
        <TextField
          label="Search by title, author or ISBN"
          value={text}
          onChangeText={setText}
          autoCapitalize="none"
          returnKeyType="search"
          maxLength={100}
        />
      </View>
      <BookGrid
        items={results.items}
        keyOf={(c) => c.copyId}
        renderItem={(c) => (
          <BookCard
            title={c.title}
            author={c.author}
            coverUrl={c.coverUrl}
            grade={c.grade}
            subtitle={ownerLine(c.owner)}
            onPress={() => router.push({ pathname: '/discover/book', params: { card: JSON.stringify(c) } })}
          />
        )}
        emptyText="Nothing here yet"
        emptyHint={q ? 'Try different words, or an ISBN.' : 'Books other people list in your city appear here.'}
        isLoading={results.isLoading}
        isError={results.isError}
        error={results.error}
        hasNextPage={!!results.hasNextPage}
        onEndReached={() => results.fetchNextPage()}
        onRetry={() => results.refetch()}
        onRefresh={() => results.refetch()}
        refreshing={results.isRefetching}
      />
    </View>
  );
}
