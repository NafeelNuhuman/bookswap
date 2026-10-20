import type { ApiClient } from '../client';
import type { Profile } from '../types';

export const getMe = (api: ApiClient) => api.request<Profile>('GET', '/api/users/me');
