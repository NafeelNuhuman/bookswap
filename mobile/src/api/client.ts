import { ApiError } from './errors';
import type { ApiErrorBody, TokenResponse } from './types';

export type TokenPair = { access: string; refresh: string };

export interface TokenStore {
  get(): Promise<TokenPair | null>;
  set(tokens: TokenPair): Promise<void>;
  clear(): Promise<void>;
}

export type RequestOptions = {
  body?: unknown;
  query?: Record<string, string | number | undefined>;
  /** Defaults to true. Login, register and refresh send no bearer token. */
  auth?: boolean;
};

export type ApiClient = {
  request<T>(method: string, path: string, opts?: RequestOptions): Promise<T>;
};

type Config = {
  baseUrl: string;
  tokens: TokenStore;
  onSignedOut: () => void;
  fetchImpl?: typeof fetch;
};

export function createApiClient({ baseUrl, tokens, onSignedOut, fetchImpl = fetch }: Config): ApiClient {
  let refreshing: Promise<void> | null = null;

  async function send(method: string, path: string, opts: RequestOptions, accessToken?: string): Promise<Response> {
    const params = Object.entries(opts.query ?? {}).filter(([, v]) => v !== undefined);
    const qs = params.length ? '?' + params.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&') : '';
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
    try {
      return await fetchImpl(baseUrl + path + qs, {
        method,
        headers,
        body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      });
    } catch {
      throw new ApiError(0, 'NETWORK', 'Network request failed');
    }
  }

  async function toError(res: Response): Promise<ApiError> {
    const text = await res.text();
    try {
      const body = JSON.parse(text) as Partial<ApiErrorBody>;
      if (body && typeof body.code === 'string') {
        return new ApiError(res.status, body.code, body.message ?? '', body.fieldErrors ?? []);
      }
    } catch {
      // not JSON: fall through
    }
    return new ApiError(res.status, `HTTP_${res.status}`, `Request failed (${res.status})`);
  }

  async function parse<T>(res: Response): Promise<T> {
    if (res.status === 204) return undefined as T;
    const text = await res.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  /** One refresh at a time: concurrent 401s wait for the same attempt. */
  function refresh(): Promise<void> {
    if (!refreshing) {
      refreshing = (async () => {
        const current = await tokens.get();
        if (!current) throw new ApiError(401, 'UNAUTHORIZED', 'Not signed in');
        const res = await send('POST', '/api/auth/refresh', { body: { refreshToken: current.refresh } });
        if (!res.ok) throw await toError(res);
        const fresh = await parse<TokenResponse>(res);
        await tokens.set({ access: fresh.accessToken, refresh: fresh.refreshToken });
      })().catch(async (e) => {
        // a refresh the server refuses ends the session (once, because this promise is shared);
        // a network or server failure leaves the user signed in so they can retry
        if (e instanceof ApiError && e.code !== 'NETWORK' && e.status < 500) await signOut();
        throw e;
      }).finally(() => {
        refreshing = null;
      });
    }
    return refreshing;
  }

  async function signOut(): Promise<void> {
    await tokens.clear();
    onSignedOut();
  }

  async function request<T>(method: string, path: string, opts: RequestOptions = {}): Promise<T> {
    const useAuth = opts.auth !== false;
    const accessToken = useAuth ? (await tokens.get())?.access : undefined;
    let res = await send(method, path, opts, accessToken);

    if (useAuth && res.status === 401) {
      const err = await toError(res);
      if (err.code !== 'UNAUTHORIZED') throw err;
      try {
        await refresh();
      } catch (refreshError) {
        throw refreshError instanceof ApiError ? refreshError : err;
      }
      res = await send(method, path, opts, (await tokens.get())?.access);
    }

    if (!res.ok) throw await toError(res);
    return parse<T>(res);
  }

  return { request };
}
