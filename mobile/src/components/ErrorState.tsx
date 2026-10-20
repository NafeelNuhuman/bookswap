import React from 'react';
import { Text, View } from 'react-native';
import { friendlyMessage } from '../api/errors';
import { theme } from '../theme';
import { Button } from './Button';

export function ErrorState({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <View style={{ alignItems: 'center', padding: 32 }}>
      <Text style={{ color: theme.colors.danger, textAlign: 'center' }}>{friendlyMessage(error)}</Text>
      <Button title="Try again" onPress={onRetry} variant="secondary" />
    </View>
  );
}
