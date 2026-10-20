import type { ApiClient } from '../client';
import type { Proposal } from '../types';

export type ProposalRequest = { requestedCopyIds: string[]; offeredCopyIds: string[]; message: string | null };

export const propose = (api: ApiClient, body: ProposalRequest) => api.request<Proposal>('POST', '/api/proposals', { body });

export const counter = (api: ApiClient, id: string, body: ProposalRequest) =>
  api.request<Proposal>('POST', `/api/proposals/${id}/counter`, { body });

export const getProposal = (api: ApiClient, id: string) => api.request<Proposal>('GET', `/api/proposals/${id}`);
