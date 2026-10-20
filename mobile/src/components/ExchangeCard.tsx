import React from 'react';
import { Pressable, Text, View } from 'react-native';
import type { Exchange } from '../api/types';
import { formatDate, summarizeItems } from '../logic/format';
import { theme } from '../theme';

export function ExchangeCard({ exchange: e, onPress }: { exchange: Exchange; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={e.with.displayName}
      style={{
        backgroundColor: theme.colors.paper, borderColor: theme.colors.border, borderWidth: 1,
        borderRadius: theme.radius.card, padding: 14, marginHorizontal: 6, marginVertical: 5,
      }}
    >
      <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
        <Text style={{ fontFamily: theme.fonts.title, fontSize: 18, color: theme.colors.ink }}>{e.with.displayName}</Text>
        <Text style={{ color: theme.colors.success }}>Completed {formatDate(e.completedAt)}</Text>
      </View>
      <Text style={{ color: theme.colors.ink, marginTop: 8 }}>{`Gave: ${summarizeItems(e.gave)}`}</Text>
      <Text style={{ color: theme.colors.ink }}>{`Got: ${summarizeItems(e.received)}`}</Text>
    </Pressable>
  );
}
