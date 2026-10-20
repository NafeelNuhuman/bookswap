import React, { useState } from 'react';
import { ActivityIndicator, Alert, Text, View } from 'react-native';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useLocalSearchParams, useRouter } from 'expo-router';
import { ApiError, friendlyMessage } from '../../../src/api/errors';
import { changeGrade, getCopy, listCopy, removeCopy, unlistCopy } from '../../../src/api/endpoints/copies';
import type { Copy } from '../../../src/api/types';
import { useApi } from '../../../src/auth/session';
import { BookCover } from '../../../src/components/BookCover';
import { Button } from '../../../src/components/Button';
import { ErrorState } from '../../../src/components/ErrorState';
import { GradePicker } from '../../../src/components/GradePicker';
import { Screen } from '../../../src/components/Screen';
import { copyActions } from '../../../src/logic/copyActions';
import { gradeLabel, statusLabel } from '../../../src/logic/format';
import { theme } from '../../../src/theme';

export default function CopyDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const api = useApi();
  const router = useRouter();
  const qc = useQueryClient();
  const [error, setError] = useState<unknown>(null);

  const q = useQuery({ queryKey: ['copies', id], queryFn: () => getCopy(api, id) });

  const update = useMutation({
    mutationFn: (fn: () => Promise<Copy>) => fn(),
    onMutate: () => setError(null),
    onSuccess: (c) => {
      qc.setQueryData(['copies', id], c);
      qc.invalidateQueries({ queryKey: ['copies', 'list'] });
    },
    onError: setError,
  });
  const remove = useMutation({
    mutationFn: () => removeCopy(api, id),
    onMutate: () => setError(null),
    onSuccess: () => {
      qc.removeQueries({ queryKey: ['copies', id] });
      qc.invalidateQueries({ queryKey: ['copies', 'list'] });
      router.back();
    },
    onError: setError,
  });

  if (q.isLoading) return <Screen><ActivityIndicator color={theme.colors.accent} style={{ marginTop: 40 }} /></Screen>;
  if (q.isError || !q.data) return <Screen><ErrorState error={q.error} onRetry={() => q.refetch()} /></Screen>;

  const c = q.data;
  const can = copyActions(c);
  const busy = update.isPending || remove.isPending;

  function confirmRemove() {
    Alert.alert('Remove this book?', 'It will leave your library for good.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Remove', style: 'destructive', onPress: () => remove.mutate() },
    ]);
  }

  return (
    <Screen scroll>
      <View style={{ width: '60%', alignSelf: 'center' }}>
        <BookCover coverUrl={c.coverUrl} title={c.title} height={260} />
      </View>
      <Text style={{ fontFamily: theme.fonts.title, fontSize: 26, color: theme.colors.ink, marginTop: 14 }}>{c.title}</Text>
      {c.author ? <Text style={{ color: theme.colors.muted, fontSize: 16 }}>{c.author}</Text> : null}
      <Text style={{ color: theme.colors.muted, marginTop: 2 }}>ISBN {c.isbn}</Text>

      <Text style={{ color: theme.colors.brown, fontWeight: '600', marginTop: 18 }}>Condition</Text>
      {can.canChangeGrade ? (
        <GradePicker value={c.grade} disabled={busy} onChange={(g) => g !== c.grade && update.mutate(() => changeGrade(api, c.id, g))} />
      ) : (
        <Text style={{ color: theme.colors.ink }}>{gradeLabel(c.grade)}</Text>
      )}

      <Text style={{ color: theme.colors.brown, fontWeight: '600', marginTop: 12 }}>Status</Text>
      <Text style={{ color: theme.colors.ink }}>{statusLabel(c.status)}</Text>
      {c.status === 'RESERVED' ? (
        <Text style={{ color: theme.colors.muted, marginTop: 4 }}>
          This book is part of an accepted exchange, so it can't be changed until that exchange ends.
        </Text>
      ) : null}

      {error ? (
        <View style={{ marginTop: 12 }}>
          <Text style={{ color: theme.colors.danger }}>{friendlyMessage(error)}</Text>
          {error instanceof ApiError && error.code === 'PROFILE_INCOMPLETE' ? (
            <Link href="/profile" style={{ color: theme.colors.accent, marginTop: 6 }}>Go to Profile</Link>
          ) : null}
        </View>
      ) : null}

      {can.canList ? <Button title="List for swapping" loading={busy} onPress={() => update.mutate(() => listCopy(api, c.id))} /> : null}
      {can.canUnlist ? <Button title="Unlist" variant="secondary" loading={busy} onPress={() => update.mutate(() => unlistCopy(api, c.id))} /> : null}
      {can.canRemove ? <Button title="Remove" variant="danger" disabled={busy} onPress={confirmRemove} /> : null}
    </Screen>
  );
}
