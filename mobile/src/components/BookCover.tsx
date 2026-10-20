import React, { useState } from 'react';
import { Image, Text, View } from 'react-native';
import { theme } from '../theme';

const TILES = ['#C98B5E', '#A9714B', '#8F6B4A', '#B97A56', '#9C7B58'];

function tileColor(title: string): string {
  let h = 0;
  for (const ch of title) h = (h * 31 + ch.charCodeAt(0)) % TILES.length;
  return TILES[h];
}

type Props = { coverUrl: string | null; title: string; height?: number };

/** The cover image, or a warm tile with the title when there is none (or it fails to load). */
export function BookCover({ coverUrl, title, height = 190 }: Props) {
  const [failed, setFailed] = useState(false);
  const box = { height, borderRadius: theme.radius.card, overflow: 'hidden' as const, width: '100%' as const };
  if (!coverUrl || failed) {
    return (
      <View style={[box, { backgroundColor: tileColor(title), justifyContent: 'center', padding: 10 }]}>
        <Text style={{ fontFamily: theme.fonts.title, color: theme.colors.paper, fontSize: 17, textAlign: 'center' }} numberOfLines={5}>
          {title}
        </Text>
      </View>
    );
  }
  return (
    <Image
      source={{ uri: coverUrl }}
      accessibilityLabel={`Cover of ${title}`}
      resizeMode="cover"
      onError={() => setFailed(true)}
      style={[box, { backgroundColor: theme.colors.border }]}
    />
  );
}
