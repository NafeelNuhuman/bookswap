import { theme } from './theme';

/** Header look shared by every tab's stack: cream background, serif brown title. */
export const stackOptions = {
  headerStyle: { backgroundColor: theme.colors.cream },
  headerTintColor: theme.colors.brown,
  headerTitleStyle: { fontFamily: theme.fonts.title, color: theme.colors.brown },
  headerShadowVisible: false,
  contentStyle: { backgroundColor: theme.colors.cream },
} as const;
