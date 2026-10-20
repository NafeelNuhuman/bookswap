import React from 'react';
import { Pressable, Text, View } from 'react-native';
import type { ConditionGrade, CopyStatus } from '../api/types';
import { gradeLabel, statusLabel } from '../logic/format';
import { theme } from '../theme';
import { BookCover } from './BookCover';

type Props = {
  title: string;
  author: string | null;
  coverUrl: string | null;
  grade?: ConditionGrade;
  status?: CopyStatus;
  /** e.g. "Sam · Leeds" on Discover */
  subtitle?: string;
  onPress: () => void;
};

const STATUS_COLOR: Record<CopyStatus, string> = {
  AVAILABLE: theme.colors.success,
  UNLISTED: theme.colors.muted,
  RESERVED: theme.colors.accent,
};

function Badge({ text, color }: { text: string; color: string }) {
  return (
    <View style={{ borderColor: color, borderWidth: 1, borderRadius: 20, paddingHorizontal: 8, paddingVertical: 2, marginRight: 6, marginTop: 6 }}>
      <Text style={{ color, fontSize: 12 }}>{text}</Text>
    </View>
  );
}

export function BookCard({ title, author, coverUrl, grade, status, subtitle, onPress }: Props) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={title} style={{ flex: 1, margin: 6 }}>
      <BookCover coverUrl={coverUrl} title={title} />
      <Text style={{ fontFamily: theme.fonts.title, fontSize: 16, color: theme.colors.ink, marginTop: 8 }} numberOfLines={2}>
        {title}
      </Text>
      {author ? <Text style={{ color: theme.colors.muted }} numberOfLines={1}>{author}</Text> : null}
      {subtitle ? <Text style={{ color: theme.colors.brown, marginTop: 2 }} numberOfLines={1}>{subtitle}</Text> : null}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
        {grade ? <Badge text={gradeLabel(grade)} color={theme.colors.brown} /> : null}
        {status ? <Badge text={statusLabel(status)} color={STATUS_COLOR[status]} /> : null}
      </View>
    </Pressable>
  );
}
