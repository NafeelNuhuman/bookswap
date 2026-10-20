import type { ApiClient } from '../client';
import type { BookLookup, ConditionGrade, Copy, CopyStatus, GradeInfo, Page } from '../types';

export type NewCopy = { isbn: string; grade: ConditionGrade; title?: string; author?: string };

export const listCopies = (api: ApiClient, opts: { status?: CopyStatus; page: number; size?: number }) =>
  api.request<Page<Copy>>('GET', '/api/copies', { query: { status: opts.status, page: opts.page, size: opts.size ?? 20 } });

export const getCopy = (api: ApiClient, id: string) => api.request<Copy>('GET', `/api/copies/${id}`);

export const addCopy = (api: ApiClient, body: NewCopy) => api.request<Copy>('POST', '/api/copies', { body });

export const changeGrade = (api: ApiClient, id: string, grade: ConditionGrade) =>
  api.request<Copy>('PUT', `/api/copies/${id}`, { body: { grade } });

export const listCopy = (api: ApiClient, id: string) => api.request<Copy>('POST', `/api/copies/${id}/list`);
export const unlistCopy = (api: ApiClient, id: string) => api.request<Copy>('POST', `/api/copies/${id}/unlist`);
export const removeCopy = (api: ApiClient, id: string) => api.request<void>('DELETE', `/api/copies/${id}`);

export const lookupBook = (api: ApiClient, isbn: string) =>
  api.request<BookLookup>('GET', '/api/books/lookup', { query: { isbn } });

export const conditionGrades = (api: ApiClient) => api.request<GradeInfo[]>('GET', '/api/condition-grades');
