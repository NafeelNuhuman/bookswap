import type { ApiClient } from '../client';
import type { CopyCard, Page } from '../types';

export const feed = (api: ApiClient, page: number) =>
  api.request<Page<CopyCard>>('GET', '/api/feed', { query: { page, size: 20 } });

export const search = (api: ApiClient, q: string, page: number) =>
  api.request<Page<CopyCard>>('GET', '/api/search', { query: { q, page, size: 20 } });

/** Everything one user currently has available. */
export const shelf = (api: ApiClient, userId: string, page: number) =>
  api.request<Page<CopyCard>>('GET', `/api/users/${userId}/copies`, { query: { page, size: 20 } });
