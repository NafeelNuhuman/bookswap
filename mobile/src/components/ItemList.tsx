import React from 'react';
import { Image, Text, View } from 'react-native';
import type { Item } from '../api/types';
import { gradeLabel } from '../logic/format';
import { theme } from '../theme';

/** The books on one side of a proposal, as they were when it was made. */
export function ItemList({ title, items }: { title: string; items: Item[] }) {
  return (
    <View style={{ marginTop: 16 }}>
      <Text style={{ fontFamily: theme.fonts.title, fontSize: 18, color: theme.colors.brown }}>{title}</Text>
      {items.map((i, n) => (
        <View key={`${i.copyId ?? 'gone'}-${n}`} style={{ flexDirection: 'row', alignItems: 'center', marginTop: 8 }}>
          {i.coverUrl ? (
            <Image source={{ uri: i.coverUrl }} style={{ width: 40, height: 58, borderRadius: 6, backgroundColor: theme.colors.border }} />
          ) : (
            <View style={{ width: 40, height: 58, borderRadius: 6, backgroundColor: theme.colors.border }} />
          )}
          <View style={{ flex: 1, marginLeft: 10 }}>
            <Text style={{ color: theme.colors.ink, fontFamily: theme.fonts.title }}>{i.title}</Text>
            <Text style={{ color: theme.colors.muted }}>{[i.author, gradeLabel(i.gradeAtProposal)].filter(Boolean).join(' · ')}</Text>
          </View>
        </View>
      ))}
    </View>
  );
}
