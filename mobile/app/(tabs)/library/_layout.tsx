import React from 'react';
import { Pressable } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Stack, useRouter } from 'expo-router';
import { stackOptions } from '../../../src/stackOptions';
import { theme } from '../../../src/theme';

function AddButton() {
  const router = useRouter();
  return (
    <Pressable accessibilityRole="button" accessibilityLabel="Add a book" onPress={() => router.push('/library/add')} hitSlop={12}>
      <Ionicons name="add-circle-outline" size={28} color={theme.colors.accent} />
    </Pressable>
  );
}

export default function LibraryLayout() {
  return (
    <Stack screenOptions={stackOptions}>
      <Stack.Screen name="index" options={{ title: 'My library', headerRight: () => <AddButton /> }} />
      <Stack.Screen name="[id]" options={{ title: 'Book' }} />
      <Stack.Screen name="add" options={{ title: 'Add a book' }} />
    </Stack>
  );
}
