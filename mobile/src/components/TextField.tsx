import React from 'react';
import { Text, TextInput, TextInputProps, View } from 'react-native';
import { theme } from '../theme';

type Props = TextInputProps & { label: string };

export function TextField({ label, style, ...rest }: Props) {
  return (
    <View style={{ marginTop: 12 }}>
      <Text style={{ color: theme.colors.muted, marginBottom: 4 }}>{label}</Text>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor={theme.colors.muted}
        style={[
          {
            backgroundColor: theme.colors.paper,
            borderColor: theme.colors.border,
            borderWidth: 1,
            borderRadius: theme.radius.button,
            paddingHorizontal: 12,
            paddingVertical: 10,
            fontSize: 16,
            color: theme.colors.ink,
          },
          style,
        ]}
        {...rest}
      />
    </View>
  );
}
