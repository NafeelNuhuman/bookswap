import React from 'react';
import { Text, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import type { CopyCard } from '../../../src/api/types';
import { BookCover } from '../../../src/components/BookCover';
import { Button } from '../../../src/components/Button';
import { EmptyState } from '../../../src/components/EmptyState';
import { Screen } from '../../../src/components/Screen';
import { gradeLabel, ownerLine } from '../../../src/logic/format';
import { theme } from '../../../src/theme';

function parseCard(raw: string | undefined): CopyCard | null {
  try {
    return raw ? (JSON.parse(raw) as CopyCard) : null;
  } catch {
    return null;
  }
}

export default function DiscoverBookScreen() {
  const { card: raw } = useLocalSearchParams<{ card: string }>();
  const router = useRouter();
  const c = parseCard(raw);
  if (!c) return <Screen><EmptyState text="This book couldn't be opened" /></Screen>;

  return (
    <Screen scroll>
      <View style={{ width: '60%', alignSelf: 'center' }}>
        <BookCover coverUrl={c.coverUrl} title={c.title} height={260} />
      </View>
      <Text style={{ fontFamily: theme.fonts.title, fontSize: 26, color: theme.colors.ink, marginTop: 14 }}>{c.title}</Text>
      {c.author ? <Text style={{ color: theme.colors.muted, fontSize: 16 }}>{c.author}</Text> : null}
      <Text style={{ color: theme.colors.muted, marginTop: 2 }}>ISBN {c.isbn}</Text>
      <Text style={{ color: theme.colors.brown, fontWeight: '600', marginTop: 18 }}>Condition</Text>
      <Text style={{ color: theme.colors.ink }}>{gradeLabel(c.grade)}</Text>
      <Text style={{ color: theme.colors.brown, fontWeight: '600', marginTop: 12 }}>Owner</Text>
      <Text style={{ color: theme.colors.ink }}>{ownerLine(c.owner)}</Text>
      <Button
        title="Propose swap"
        onPress={() => router.push({ pathname: '/compose', params: { recipientId: c.owner.id, wantedCopyId: c.copyId } })}
      />
    </Screen>
  );
}
