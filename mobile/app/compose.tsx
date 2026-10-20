import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Text } from 'react-native';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { listCopies } from '../src/api/endpoints/copies';
import { shelf } from '../src/api/endpoints/discovery';
import { counter, getProposal, propose } from '../src/api/endpoints/proposals';
import { friendlyMessage } from '../src/api/errors';
import type { CopyCard } from '../src/api/types';
import { usePagedQuery } from '../src/api/usePagedQuery';
import { useApi } from '../src/auth/session';
import { Button } from '../src/components/Button';
import { ErrorState } from '../src/components/ErrorState';
import { Screen } from '../src/components/Screen';
import { BookRow, SelectableBookRow } from '../src/components/SelectableBookRow';
import { TextField } from '../src/components/TextField';
import { MAX_MESSAGE, MAX_PER_SIDE, Selection, Side, canSubmit, toRequest, toggle } from '../src/logic/compose';
import { theme } from '../src/theme';

function mergeRows(loaded: BookRow[], pinned: BookRow[]): BookRow[] {
  const seen = new Set(loaded.map((r) => r.id));
  return [...pinned.filter((r) => !seen.has(r.id)), ...loaded];
}

/** One screen for both a new proposal and a counter-offer (when `counterOf` is given). */
export default function ComposeScreen() {
  const params = useLocalSearchParams<{ recipientId?: string; wantedCopyId?: string; counterOf?: string }>();
  const counterOf = params.counterOf;
  const api = useApi();
  const router = useRouter();
  const qc = useQueryClient();

  const original = useQuery({ queryKey: ['proposals', counterOf], queryFn: () => getProposal(api, counterOf!), enabled: !!counterOf });
  const recipientId = counterOf ? original.data?.proposer.id : params.recipientId;

  const [sel, setSel] = useState<Selection>({ requested: params.wantedCopyId ? [params.wantedCopyId] : [], offered: [] });
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // A counter starts from the proposal being answered: I offer what they asked of me and ask for what they offered.
  const prefilled = useRef(false);
  useEffect(() => {
    if (original.data && !prefilled.current) {
      prefilled.current = true;
      setSel({
        requested: original.data.offered.map((i) => i.copyId).filter((id): id is string => !!id),
        offered: original.data.requested.map((i) => i.copyId).filter((id): id is string => !!id),
      });
    }
  }, [original.data]);

  const theirs = usePagedQuery<CopyCard>(['compose', 'shelf', recipientId], (p) => shelf(api, recipientId!, p), { enabled: !!recipientId });
  const mine = usePagedQuery(['compose', 'mine'], (p) => listCopies(api, { status: 'AVAILABLE', page: p }));

  const theirRows = useMemo(() => mergeRows(
    theirs.items.map((c) => ({ id: c.copyId, title: c.title, author: c.author, coverUrl: c.coverUrl, grade: c.grade })),
    (original.data?.offered ?? []).filter((i) => i.copyId).map((i) => ({ id: i.copyId!, title: i.title, author: i.author, coverUrl: i.coverUrl, grade: i.gradeAtProposal })),
  ), [theirs.items, original.data]);
  const myRows = useMemo(() => mergeRows(
    mine.items.map((c) => ({ id: c.id, title: c.title, author: c.author, coverUrl: c.coverUrl, grade: c.grade })),
    (original.data?.requested ?? []).filter((i) => i.copyId).map((i) => ({ id: i.copyId!, title: i.title, author: i.author, coverUrl: i.coverUrl, grade: i.gradeAtProposal })),
  ), [mine.items, original.data]);

  const theirName = original.data?.proposer.displayName ?? theirs.items[0]?.owner.displayName ?? 'them';

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      const body = toRequest(sel, message);
      await (counterOf ? counter(api, counterOf, body) : propose(api, body));
      qc.invalidateQueries({ queryKey: ['proposals'] });
      qc.invalidateQueries({ queryKey: ['copies'] });
      router.dismissTo({ pathname: '/exchanges', params: { section: 'outgoing' } });
    } catch (e) {
      setError(friendlyMessage(e));
      setBusy(false);
    }
  }

  if (counterOf && original.isLoading) return <Screen><ActivityIndicator color={theme.colors.accent} style={{ marginTop: 40 }} /></Screen>;
  if (counterOf && original.isError) return <Screen><ErrorState error={original.error} onRetry={() => original.refetch()} /></Screen>;

  const heading = { fontFamily: theme.fonts.title, fontSize: 20, color: theme.colors.brown, marginTop: 18 } as const;
  const hint = { color: theme.colors.muted, marginTop: 2 } as const;

  return (
    <Screen scroll>
      <Text style={heading}>Books you want from {theirName}</Text>
      <Text style={hint}>{sel.requested.length} of {MAX_PER_SIDE} chosen</Text>
      {theirRows.map((r) => (
        <SelectableBookRow key={r.id} row={r} checked={sel.requested.includes(r.id)} onToggle={() => setSel((s) => toggle(s, 'requested' as Side, r.id))} />
      ))}
      {theirs.isError ? <ErrorState error={theirs.error} onRetry={() => theirs.refetch()} /> : null}
      {theirs.hasNextPage ? <Button title="Show more of their books" variant="secondary" onPress={() => theirs.fetchNextPage()} /> : null}

      <Text style={heading}>Books you offer</Text>
      <Text style={hint}>{sel.offered.length} of {MAX_PER_SIDE} chosen. Only your available books are listed.</Text>
      {myRows.map((r) => (
        <SelectableBookRow key={r.id} row={r} checked={sel.offered.includes(r.id)} onToggle={() => setSel((s) => toggle(s, 'offered' as Side, r.id))} />
      ))}
      {!mine.isLoading && myRows.length === 0 ? <Text style={hint}>You have no available books to offer. List one from your library first.</Text> : null}
      {mine.isError ? <ErrorState error={mine.error} onRetry={() => mine.refetch()} /> : null}
      {mine.hasNextPage ? <Button title="Show more of my books" variant="secondary" onPress={() => mine.fetchNextPage()} /> : null}

      <TextField label="Message (optional)" value={message} onChangeText={setMessage} maxLength={MAX_MESSAGE} multiline />
      {error ? <Text style={{ color: theme.colors.danger, marginTop: 10 }}>{error}</Text> : null}
      <Button title={counterOf ? 'Send counter-offer' : 'Send proposal'} onPress={submit} loading={busy} disabled={!canSubmit(sel)} />
    </Screen>
  );
}
