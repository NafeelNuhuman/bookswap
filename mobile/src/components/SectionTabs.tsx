import React from 'react';
import { Pressable, Text, View } from 'react-native';
import { theme } from '../theme';

type Props<K extends string> = { sections: { key: K; label: string }[]; value: K; onChange: (k: K) => void };

/** A row of sections along the top of a tab. */
export function SectionTabs<K extends string>({ sections, value, onChange }: Props<K>) {
  return (
    <View style={{ flexDirection: 'row', paddingHorizontal: 12, paddingBottom: 6 }}>
      {sections.map((s) => {
        const on = s.key === value;
        return (
          <Pressable
            key={s.key}
            accessibilityRole="tab"
            accessibilityLabel={s.label}
            accessibilityState={{ selected: on }}
            onPress={() => onChange(s.key)}
            style={{ flex: 1, alignItems: 'center', paddingVertical: 8, borderBottomWidth: 2, borderBottomColor: on ? theme.colors.accent : theme.colors.border }}
          >
            <Text style={{ color: on ? theme.colors.accent : theme.colors.muted, fontWeight: on ? '700' : '400' }}>{s.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}
