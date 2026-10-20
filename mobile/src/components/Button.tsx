import React from 'react';
import { ActivityIndicator, Pressable, Text } from 'react-native';
import { theme } from '../theme';

type Props = {
  title: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  variant?: 'primary' | 'secondary' | 'danger';
};

export function Button({ title, onPress, loading = false, disabled = false, variant = 'primary' }: Props) {
  const inactive = loading || disabled;
  const bg = variant === 'primary' ? theme.colors.accent : variant === 'danger' ? theme.colors.danger : 'transparent';
  const fg = variant === 'secondary' ? theme.colors.brown : theme.colors.paper;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled: inactive }}
      disabled={inactive}
      onPress={onPress}
      style={{
        backgroundColor: bg,
        borderColor: variant === 'secondary' ? theme.colors.brown : bg,
        borderWidth: 1,
        borderRadius: theme.radius.button,
        paddingVertical: 12,
        paddingHorizontal: 18,
        alignItems: 'center',
        opacity: inactive ? 0.55 : 1,
        marginTop: 10,
      }}
    >
      {loading ? <ActivityIndicator color={fg} /> : <Text style={{ color: fg, fontWeight: '600', fontSize: 16 }}>{title}</Text>}
    </Pressable>
  );
}
