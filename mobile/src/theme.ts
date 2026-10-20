import { Platform } from 'react-native';

export const theme = {
  colors: {
    cream: '#F6EEDD',
    paper: '#FFFAF0',
    brown: '#4A3426',
    ink: '#2B1D14',
    accent: '#B5532F',
    muted: '#8A7563',
    danger: '#A12D2D',
    success: '#4F7A4A',
    border: '#E2D5BC',
  },
  fonts: {
    title: Platform.select({ ios: 'Georgia', default: 'serif' }) as string,
    body: Platform.select({ ios: 'System', default: 'sans-serif' }) as string,
  },
  radius: { card: 14, button: 12 },
} as const;
