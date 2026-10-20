import React from 'react';
import { ActivityIndicator, FlatList, RefreshControl, View } from 'react-native';
import { theme } from '../theme';
import { EmptyState } from './EmptyState';
import { ErrorState } from './ErrorState';

type Props<T> = {
  items: T[];
  keyOf: (item: T) => string;
  renderItem: (item: T) => React.ReactElement | null;
  emptyText: string;
  emptyHint?: string;
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  hasNextPage: boolean;
  onEndReached: () => void;
  onRetry: () => void;
  onRefresh?: () => void;
  refreshing?: boolean;
  columns?: number;
  header?: React.ReactElement | null;
};

/** Two-column grid with infinite scroll, loading, empty and error states. */
export function BookGrid<T>({
  items, keyOf, renderItem, emptyText, emptyHint, isLoading, isError, error, hasNextPage,
  onEndReached, onRetry, onRefresh, refreshing = false, columns = 2, header,
}: Props<T>) {
  let body: React.ReactElement | null = null;
  if (isError && items.length === 0) body = <ErrorState error={error} onRetry={onRetry} />;
  else if (isLoading) body = <ActivityIndicator color={theme.colors.accent} style={{ marginTop: 40 }} />;
  else if (items.length === 0) body = <EmptyState text={emptyText} hint={emptyHint} />;

  return (
    <FlatList
      data={items}
      key={columns}
      numColumns={columns}
      keyExtractor={keyOf}
      renderItem={({ item }) => <View style={{ flex: 1 / columns }}>{renderItem(item)}</View>}
      onEndReached={() => { if (hasNextPage) onEndReached(); }}
      onEndReachedThreshold={0.4}
      ListHeaderComponent={header ?? undefined}
      ListEmptyComponent={body ?? undefined}
      refreshControl={onRefresh ? <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.colors.accent} /> : undefined}
      contentContainerStyle={{ padding: 8, flexGrow: 1 }}
    />
  );
}
