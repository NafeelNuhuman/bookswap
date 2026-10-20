import type { ApiClient } from '../client';
import type { Profile } from '../types';

export const getMe = (api: ApiClient) => api.request<Profile>('GET', '/api/users/me');

export type ProfileUpdate = { displayName: string; city: string; postalCode: string; contactNote: string };

export const updateMe = (api: ApiClient, body: ProfileUpdate) => api.request<Profile>('PUT', '/api/users/me', { body });

/** The account is anonymised and its books removed; the current password is required. */
export const deleteMe = (api: ApiClient, password: string) =>
  api.request<void>('DELETE', '/api/users/me', { body: { password } });
