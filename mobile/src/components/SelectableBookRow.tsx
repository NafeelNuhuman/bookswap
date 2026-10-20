import React from 'react';
import { Image, Pressable, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { ConditionGrade } from '../api/types';
import { gradeLabel } from '../logic/format';
import { theme } from '../theme';

export type BookRow = { id: string; title: string; author: string | null; coverUrl: string | null; grade: ConditionGrade };

type Props = { row: BookRow; checked: boolean; onToggle: () => void };

export function SelectableBookRow({ row, checked, onToggle }: Props) {
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityLabel={row.title}
      accessibilityState={{ checked }}
      onPress={onToggle}
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        padding: 8,
        marginTop: 8,
        borderRadius: theme.radius.card,
        borderWidth: 1,
        borderColor: checked ? theme.colors.accent : theme.colors.border,
        backgroundColor: theme.colors.paper,
      }}
    >
      {row.coverUrl ? (
        <Image source={{ uri: row.coverUrl }} style={{ width: 40, height: 58, borderRadius: 6, backgroundColor: theme.colors.border }} />
      ) : (
        <View style={{ width: 40, height: 58, borderRadius: 6, backgroundColor: theme.colors.border }} />
      )}
      <View style={{ flex: 1, marginHorizontal: 10 }}>
        <Text style={{ fontFamily: theme.fonts.title, color: theme.colors.ink }} numberOfLines={2}>{row.title}</Text>
        <Text style={{ color: theme.colors.muted }} numberOfLines={1}>
          {[row.author, gradeLabel(row.grade)].filter(Boolean).join(' · ')}
        </Text>
      </View>
      <Ionicons name={checked ? 'checkbox' : 'square-outline'} size={24} color={checked ? theme.colors.accent : theme.colors.muted} />
    </Pressable>
  );
}
