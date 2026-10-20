import React, { useEffect, useState } from 'react';
import { Text, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { listExchanges, listProposals, Role } from '../../../src/api/endpoints/proposals';
import type { Proposal, ProposalStatus } from '../../../src/api/types';
import { usePagedQuery } from '../../../src/api/usePagedQuery';
import { useApi } from '../../../src/auth/session';
import { BookGrid } from '../../../src/components/BookGrid';
import { ExchangeCard } from '../../../src/components/ExchangeCard';
import { ProposalCard } from '../../../src/components/ProposalCard';
import { SectionTabs } from '../../../src/components/SectionTabs';
import { theme } from '../../../src/theme';

type Section = 'incoming' | 'outgoing' | 'active' | 'history';
const SECTIONS: { key: Section; label: string }[] = [
  { key: 'incoming', label: 'Incoming' },
  { key: 'outgoing', label: 'Outgoing' },
  { key: 'active', label: 'Active' },
  { key: 'history', label: 'History' },
];
const isSection = (s: unknown): s is Section => SECTIONS.some((x) => x.key === s);

function useProposals(role: Role, status?: ProposalStatus) {
  const api = useApi();
  return usePagedQuery<Proposal>(['proposals', 'list', role, status ?? 'all'], (page) => listProposals(api, { role, status, page }));
}

function ProposalList({ role, emptyText }: { role: Role; emptyText: string }) {
  const router = useRouter();
  const q = useProposals(role);
  return (
    <BookGrid
      columns={1}
      items={q.items}
      keyOf={(p) => p.id}
      renderItem={(p) => <ProposalCard proposal={p} perspective={role} onPress={() => router.push({ pathname: '/exchanges/[id]', params: { id: p.id } })} />}
      emptyText={emptyText}
      isLoading={q.isLoading}
      isError={q.isError}
      error={q.error}
      hasNextPage={!!q.hasNextPage}
      onEndReached={() => q.fetchNextPage()}
      onRetry={() => q.refetch()}
      onRefresh={() => q.refetch()}
      refreshing={q.isRefetching}
    />
  );
}

type Row = { key: string; header?: string; proposal?: Proposal; perspective?: Role };

/** Accepted exchanges: what I received and what I proposed, each its own paginated query (the API lists per role). */
function ActiveList() {
  const router = useRouter();
  const received = useProposals('incoming', 'ACCEPTED');
  const proposed = useProposals('outgoing', 'ACCEPTED');
  const rows: Row[] = [];
  if (received.items.length) {
    rows.push({ key: 'h-in', header: 'You received' });
    received.items.forEach((p) => rows.push({ key: `in-${p.id}`, proposal: p, perspective: 'incoming' }));
  }
  if (proposed.items.length) {
    rows.push({ key: 'h-out', header: 'You proposed' });
    proposed.items.forEach((p) => rows.push({ key: `out-${p.id}`, proposal: p, perspective: 'outgoing' }));
  }
  return (
    <BookGrid
      columns={1}
      items={rows}
      keyOf={(r) => r.key}
      renderItem={(r) =>
        r.header ? (
          <Text style={{ fontFamily: theme.fonts.title, fontSize: 16, color: theme.colors.brown, marginTop: 10, marginLeft: 8 }}>{r.header}</Text>
        ) : (
          <ProposalCard proposal={r.proposal!} perspective={r.perspective!} onPress={() => router.push({ pathname: '/exchanges/[id]', params: { id: r.proposal!.id } })} />
        )
      }
      emptyText="No accepted exchanges right now"
      emptyHint="Once a proposal is accepted it shows up here until you both confirm."
      isLoading={received.isLoading || proposed.isLoading}
      isError={received.isError && proposed.isError}
      error={received.error ?? proposed.error}
      hasNextPage={!!(received.hasNextPage || proposed.hasNextPage)}
      onEndReached={() => {
        if (received.hasNextPage) received.fetchNextPage();
        if (proposed.hasNextPage) proposed.fetchNextPage();
      }}
      onRetry={() => { received.refetch(); proposed.refetch(); }}
      onRefresh={() => { received.refetch(); proposed.refetch(); }}
      refreshing={received.isRefetching || proposed.isRefetching}
    />
  );
}

function HistoryList() {
  const api = useApi();
  const router = useRouter();
  const q = usePagedQuery(['exchanges', 'list'], (page) => listExchanges(api, page));
  return (
    <BookGrid
      columns={1}
      items={q.items}
      keyOf={(e) => e.proposalId}
      renderItem={(e) => <ExchangeCard exchange={e} onPress={() => router.push({ pathname: '/exchanges/[id]', params: { id: e.proposalId } })} />}
      emptyText="No completed swaps yet"
      emptyHint="Your finished exchanges are kept here."
      isLoading={q.isLoading}
      isError={q.isError}
      error={q.error}
      hasNextPage={!!q.hasNextPage}
      onEndReached={() => q.fetchNextPage()}
      onRetry={() => q.refetch()}
      onRefresh={() => q.refetch()}
      refreshing={q.isRefetching}
    />
  );
}

export default function ExchangesScreen() {
  const { section: param } = useLocalSearchParams<{ section?: string }>();
  const [section, setSection] = useState<Section>(isSection(param) ? param : 'incoming');
  useEffect(() => {
    if (isSection(param)) setSection(param);
  }, [param]);

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.cream }}>
      <SectionTabs sections={SECTIONS} value={section} onChange={setSection} />
      {section === 'incoming' ? <ProposalList role="incoming" emptyText="No proposals waiting for you" /> : null}
      {section === 'outgoing' ? <ProposalList role="outgoing" emptyText="You haven't proposed anything yet" /> : null}
      {section === 'active' ? <ActiveList /> : null}
      {section === 'history' ? <HistoryList /> : null}
    </View>
  );
}
