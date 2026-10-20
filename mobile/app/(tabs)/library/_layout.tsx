import { Stack } from 'expo-router';
import { stackOptions } from '../../../src/stackOptions';

export default function LibraryLayout() {
  return (
    <Stack screenOptions={stackOptions}>
      <Stack.Screen name="index" options={{ title: 'Library' }} />
    </Stack>
  );
}
