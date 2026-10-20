import React from 'react';
import { Text, View } from 'react-native';
import { theme } from '../theme';

export function EmptyState({ text, hint }: { text: string; hint?: string }) {
  return (
    <View style={{ alignItems: 'center', padding: 32 }}>
      <Text style={{ fontFamily: theme.fonts.title, fontSize: 18, color: theme.colors.brown, textAlign: 'center' }}>{text}</Text>
      {hint ? <Text style={{ color: theme.colors.muted, marginTop: 6, textAlign: 'center' }}>{hint}</Text> : null}
    </View>
  );
}
