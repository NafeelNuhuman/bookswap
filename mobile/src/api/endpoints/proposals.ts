import type { ApiClient } from '../client';
import type { Exchange, Page, Proposal, ProposalStatus } from '../types';

export type ProposalRequest = { requestedCopyIds: string[]; offeredCopyIds: string[]; message: string | null };

export const propose = (api: ApiClient, body: ProposalRequest) => api.request<Proposal>('POST', '/api/proposals', { body });

export const counter = (api: ApiClient, id: string, body: ProposalRequest) =>
  api.request<Proposal>('POST', `/api/proposals/${id}/counter`, { body });

export const getProposal = (api: ApiClient, id: string) => api.request<Proposal>('GET', `/api/proposals/${id}`);

export type Role = 'incoming' | 'outgoing';

export const listProposals = (api: ApiClient, opts: { role: Role; status?: ProposalStatus; page: number }) =>
  api.request<Page<Proposal>>('GET', '/api/proposals', { query: { role: opts.role, status: opts.status, page: opts.page, size: 20 } });

export const listExchanges = (api: ApiClient, page: number) =>
  api.request<Page<Exchange>>('GET', '/api/exchanges', { query: { page, size: 20 } });
