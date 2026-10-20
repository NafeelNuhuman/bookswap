import React from 'react';
import { Pressable, Text, View } from 'react-native';
import type { Proposal } from '../api/types';
import { formatDate, proposalStatusLabel, summarizeItems } from '../logic/format';
import { theme } from '../theme';

type Props = { proposal: Proposal; perspective: 'incoming' | 'outgoing'; onPress: () => void };

const card = {
  backgroundColor: theme.colors.paper,
  borderColor: theme.colors.border,
  borderWidth: 1,
  borderRadius: theme.radius.card,
  padding: 14,
  marginHorizontal: 6,
  marginVertical: 5,
} as const;

export function ProposalCard({ proposal: p, perspective, onPress }: Props) {
  const other = perspective === 'incoming' ? p.proposer : p.recipient;
  const settling = p.status === 'ACCEPTED' && !!p.confirmBy && new Date(p.confirmBy).getTime() <= Date.now();
  const theirLine = perspective === 'incoming' ? 'They offer' : 'You get';
  const myLine = perspective === 'incoming' ? 'You give' : 'You offer';
  const theirs = perspective === 'incoming' ? p.offered : p.requested;
  const mine = perspective === 'incoming' ? p.requested : p.offered;
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={other.displayName} style={card}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
        <Text style={{ fontFamily: theme.fonts.title, fontSize: 18, color: theme.colors.ink }}>{other.displayName}</Text>
        <Text style={{ color: theme.colors.accent }}>{settling ? 'Settling soon' : proposalStatusLabel(p.status)}</Text>
      </View>
      <Text style={{ color: theme.colors.muted, marginTop: 8 }}>{theirLine}</Text>
      <Text style={{ color: theme.colors.ink }}>{summarizeItems(theirs)}</Text>
      <Text style={{ color: theme.colors.muted, marginTop: 6 }}>{myLine}</Text>
      <Text style={{ color: theme.colors.ink }}>{summarizeItems(mine)}</Text>
      <Text style={{ color: theme.colors.muted, marginTop: 8, fontSize: 12 }}>{formatDate(p.createdAt)}</Text>
    </Pressable>
  );
}
