import React, { useState } from 'react';
import { ActivityIndicator, Alert, Text, View } from 'react-native';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { accept, cancel, confirm, getProposal, reject, setPickupNote } from '../../../src/api/endpoints/proposals';
import { getMe } from '../../../src/api/endpoints/profile';
import { friendlyMessage } from '../../../src/api/errors';
import type { Proposal } from '../../../src/api/types';
import { useApi } from '../../../src/auth/session';
import { Button } from '../../../src/components/Button';
import { ErrorState } from '../../../src/components/ErrorState';
import { ItemList } from '../../../src/components/ItemList';
import { PickupNoteEditor } from '../../../src/components/PickupNoteEditor';
import { Screen } from '../../../src/components/Screen';
import { TextField } from '../../../src/components/TextField';
import { effectiveStatusLabel, formatDate } from '../../../src/logic/format';
import { proposalActions } from '../../../src/logic/proposalActions';
import { theme } from '../../../src/theme';

const label = { color: theme.colors.brown, fontWeight: '600', marginTop: 16 } as const;

export default function ExchangeDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const api = useApi();
  const router = useRouter();
  const qc = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [declining, setDeclining] = useState(false);
  const [reason, setReason] = useState('');

  const detail = useQuery({ queryKey: ['proposals', 'detail', id], queryFn: () => getProposal(api, id) });
  const me = useQuery({ queryKey: ['me'], queryFn: () => getMe(api) });

  async function run(fn: () => Promise<Proposal>) {
    setBusy(true);
    setError(null);
    try {
      const updated = await fn();
      qc.setQueryData(['proposals', 'detail', id], updated);
      qc.invalidateQueries({ queryKey: ['proposals', 'list'] });
      qc.invalidateQueries({ queryKey: ['exchanges'] });
      qc.invalidateQueries({ queryKey: ['copies'] });
      setDeclining(false);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  if (detail.isLoading || me.isLoading) return <Screen><ActivityIndicator color={theme.colors.accent} style={{ marginTop: 40 }} /></Screen>;
  if (detail.isError || !detail.data) return <Screen><ErrorState error={detail.error} onRetry={() => detail.refetch()} /></Screen>;
  if (me.isError || !me.data) return <Screen><ErrorState error={me.error} onRetry={() => me.refetch()} /></Screen>;

  const p = detail.data;
  const now = new Date();
  const mine = p.proposer.id === me.data.id;
  const other = mine ? p.recipient : p.proposer;
  const can = proposalActions(p, me.data.id, now);
  const hasContact = !!(other.email || other.contactNote);

  // What each side gives, from my point of view.
  const iGive = mine ? p.offered : p.requested;
  const iGet = mine ? p.requested : p.offered;
  const done = p.status === 'COMPLETED';

  function confirmCancel() {
    Alert.alert('Cancel this exchange?', 'The books go back on both shelves.', [
      { text: 'Keep it', style: 'cancel' },
      { text: 'Cancel exchange', style: 'destructive', onPress: () => run(() => cancel(api, p.id)) },
    ]);
  }

  return (
    <Screen scroll>
      <Text style={{ fontFamily: theme.fonts.title, fontSize: 26, color: theme.colors.ink }}>{other.displayName}</Text>
      {other.city ? <Text style={{ color: theme.colors.muted }}>{other.city}</Text> : null}
      <Text style={{ color: theme.colors.accent, marginTop: 4 }}>{effectiveStatusLabel(p, now)}</Text>

      <ItemList title={done ? 'You received' : mine ? 'You get' : 'They offer'} items={iGet} />
      <ItemList title={done ? 'You gave' : mine ? 'You offer' : 'You give'} items={iGive} />

      {p.message ? <><Text style={label}>Message</Text><Text style={{ color: theme.colors.ink }}>{p.message}</Text></> : null}
      {p.status === 'REJECTED' && p.rejectReason ? <><Text style={label}>Reason</Text><Text style={{ color: theme.colors.ink }}>{p.rejectReason}</Text></> : null}
      {p.status === 'CANCELLED' ? (
        <Text style={{ color: theme.colors.muted, marginTop: 12 }}>
          {p.cancelCause === 'NO_SHOW' ? 'Nobody confirmed in time, so it was cancelled.'
            : p.cancelCause === 'COPY_UNAVAILABLE' ? 'A book became unavailable.' : 'It was called off.'}
        </Text>
      ) : null}
      {done ? <Text style={{ color: theme.colors.success, marginTop: 12 }}>Completed {formatDate(p.completedAt)}</Text> : null}

      {p.status === 'ACCEPTED' ? (
        <View>
          {hasContact ? (
            <>
              <Text style={label}>Contact details</Text>
              {other.email ? <Text style={{ color: theme.colors.ink }}>{other.email}</Text> : null}
              {other.contactNote ? <Text style={{ color: theme.colors.ink }}>{other.contactNote}</Text> : null}
            </>
          ) : null}
          {p.confirmBy ? (
            <Text style={{ color: theme.colors.muted, marginTop: 12 }}>
              Confirm by {formatDate(p.confirmBy)}. After that it completes by itself if one of you confirmed, or is cancelled if nobody did.
            </Text>
          ) : null}
          {p.confirmedByMe ? <Text style={{ color: theme.colors.success, marginTop: 8 }}>Waiting for the other person to confirm.</Text> : null}
          {p.confirmedByOther && !p.confirmedByMe ? (
            <Text style={{ color: theme.colors.brown, marginTop: 8 }}>{other.displayName} says the swap happened. Confirm if it did.</Text>
          ) : null}
        </View>
      ) : null}

      {can.editPickupNote ? (
        <PickupNoteEditor initial={p.pickupNote} busy={busy} onSave={(note) => run(() => setPickupNote(api, p.id, note))} />
      ) : p.pickupNote ? (
        <><Text style={label}>Pickup note</Text><Text style={{ color: theme.colors.ink }}>{p.pickupNote}</Text></>
      ) : null}

      {error ? <Text style={{ color: theme.colors.danger, marginTop: 12 }}>{friendlyMessage(error)}</Text> : null}

      {can.accept ? <Button title="Accept" loading={busy} onPress={() => run(() => accept(api, p.id))} /> : null}
      {can.counter ? <Button title="Counter-offer" variant="secondary" disabled={busy} onPress={() => router.push({ pathname: '/compose', params: { counterOf: p.id } })} /> : null}
      {can.reject && !declining ? <Button title="Decline" variant="secondary" disabled={busy} onPress={() => setDeclining(true)} /> : null}
      {can.reject && declining ? (
        <>
          <TextField label="Reason (optional)" value={reason} onChangeText={setReason} maxLength={500} />
          <Button title="Send decline" variant="danger" loading={busy} onPress={() => run(() => reject(api, p.id, reason.trim()))} />
        </>
      ) : null}
      {can.confirm ? <Button title="Confirm the swap" loading={busy} onPress={() => run(() => confirm(api, p.id))} /> : null}
      {can.cancel && p.status === 'PROPOSED' ? <Button title="Cancel proposal" variant="secondary" loading={busy} onPress={() => run(() => cancel(api, p.id))} /> : null}
      {can.cancel && p.status === 'ACCEPTED' ? <Button title="Cancel exchange" variant="danger" disabled={busy} onPress={confirmCancel} /> : null}
    </Screen>
  );
}
