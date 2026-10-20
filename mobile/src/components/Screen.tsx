import React from 'react';
import { KeyboardAvoidingView, ScrollView, StyleProp, View, ViewStyle } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { theme } from '../theme';

type Props = { children: React.ReactNode; scroll?: boolean; style?: StyleProp<ViewStyle> };

/** Cream page background with safe-area padding; scroll for forms and detail pages. */
export function Screen({ children, scroll = false, style }: Props) {
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.colors.cream }} edges={['top', 'left', 'right']}>
      <KeyboardAvoidingView behavior="padding" style={{ flex: 1 }}>
        {scroll ? (
          <ScrollView contentContainerStyle={[{ padding: 20 }, style]} keyboardShouldPersistTaps="handled">
            {children}
          </ScrollView>
        ) : (
          <View style={[{ flex: 1, padding: 20 }, style]}>{children}</View>
        )}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
