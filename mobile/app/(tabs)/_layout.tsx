import { Ionicons } from '@expo/vector-icons';
import { Tabs } from 'expo-router';
import { useOpenProfileWhenIncomplete } from '../../src/logic/useOpenProfileWhenIncomplete';
import { theme } from '../../src/theme';

type IconName = React.ComponentProps<typeof Ionicons>['name'];

function tab(name: string, title: string, icon: IconName) {
  return (
    <Tabs.Screen
      name={name}
      options={{ title, tabBarIcon: ({ color, size }) => <Ionicons name={icon} size={size} color={color as string} /> }}
    />
  );
}

export default function TabsLayout() {
  useOpenProfileWhenIncomplete();
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: theme.colors.accent,
        tabBarInactiveTintColor: theme.colors.muted,
        tabBarStyle: { backgroundColor: theme.colors.paper, borderTopColor: theme.colors.border },
      }}
    >
      {tab('library', 'Library', 'book-outline')}
      {tab('discover', 'Discover', 'search-outline')}
      {tab('exchanges', 'Exchanges', 'swap-horizontal-outline')}
      {tab('profile', 'Profile', 'person-outline')}
    </Tabs>
  );
}
