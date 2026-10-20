import { createApiClient, TokenStore } from '../src/api/client';
import { ApiError } from '../src/api/errors';

type Call = { url: string; init: RequestInit };

function reply(status: number, body?: unknown) {
  return { status, ok: status < 400, text: async () => (body === undefined ? '' : JSON.stringify(body)) } as Response;
}

function errorBody(status: number, code: string) {
  return { status, code, message: code + ' message', fieldErrors: [], timestamp: 't', path: '/x' };
}

function memoryTokens(initial: { access: string; refresh: string } | null): TokenStore & { current: typeof initial } {
  const store = {
    current: initial,
    get: async () => store.current,
    set: async (t: { access: string; refresh: string }) => { store.current = t; },
    clear: async () => { store.current = null; },
  };
  return store;
}

function setup(handler: (call: Call, n: number) => Response | Promise<Response>, tokens = memoryTokens({ access: 'a1', refresh: 'r1' })) {
  const calls: Call[] = [];
  const fetchImpl = jest.fn(async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    return handler({ url, init }, calls.length);
  });
  const onSignedOut = jest.fn();
  const client = createApiClient({ baseUrl: 'http://host:8080', tokens, onSignedOut, fetchImpl: fetchImpl as unknown as typeof fetch });
  return { client, calls, tokens, onSignedOut };
}

const isRefresh = (c: Call) => c.url.endsWith('/api/auth/refresh');
const header = (c: Call, name: string) => (c.init.headers as Record<string, string>)[name];
const newPair = { accessToken: 'a2', refreshToken: 'r2', tokenType: 'Bearer', expiresInSeconds: 900 };

describe('api client', () => {
  it('sends the bearer token and a JSON body', async () => {
    const { client, calls } = setup(() => reply(200, { ok: true }));
    await client.request('POST', '/api/copies', { body: { isbn: '1' }, query: { page: 2, q: undefined } });
    expect(calls[0].url).toBe('http://host:8080/api/copies?page=2');
    expect(header(calls[0], 'Authorization')).toBe('Bearer a1');
    expect(header(calls[0], 'Content-Type')).toBe('application/json');
    expect(calls[0].init.body).toBe(JSON.stringify({ isbn: '1' }));
  });

  it('resolves 204 to undefined', async () => {
    const { client } = setup(() => reply(204));
    await expect(client.request('DELETE', '/api/users/me', { body: { password: 'x' } })).resolves.toBeUndefined();
  });

  it('a 401 UNAUTHORIZED triggers one refresh, retries once with the new token and stores the new pair', async () => {
    const { client, calls, tokens } = setup((c) => {
      if (isRefresh(c)) return reply(200, newPair);
      return header(c, 'Authorization') === 'Bearer a2' ? reply(200, { done: 1 }) : reply(401, errorBody(401, 'UNAUTHORIZED'));
    });
    await expect(client.request('GET', '/api/copies')).resolves.toEqual({ done: 1 });
    expect(calls.filter(isRefresh)).toHaveLength(1);
    expect(JSON.parse(calls.find(isRefresh)!.init.body as string)).toEqual({ refreshToken: 'r1' });
    expect(header(calls.find(isRefresh)!, 'Authorization')).toBeUndefined();
    expect(tokens.current).toEqual({ access: 'a2', refresh: 'r2' });
  });

  it('a 401 INVALID_CREDENTIALS is thrown as ApiError without any refresh call', async () => {
    const { client, calls } = setup(() => reply(401, errorBody(401, 'INVALID_CREDENTIALS')));
    await expect(client.request('POST', '/api/auth/login', { body: {}, auth: false })).rejects.toMatchObject({ status: 401, code: 'INVALID_CREDENTIALS' });
    await expect(client.request('DELETE', '/api/users/me', { body: {} })).rejects.toBeInstanceOf(ApiError);
    expect(calls.filter(isRefresh)).toHaveLength(0);
  });

  it('five concurrent 401s cause exactly one refresh call', async () => {
    const { client, calls } = setup((c) => {
      if (isRefresh(c)) return reply(200, newPair);
      return header(c, 'Authorization') === 'Bearer a2' ? reply(200, {}) : reply(401, errorBody(401, 'UNAUTHORIZED'));
    });
    await Promise.all([1, 2, 3, 4, 5].map(() => client.request('GET', '/api/copies')));
    expect(calls.filter(isRefresh)).toHaveLength(1);
  });

  it('a failed refresh clears the tokens, calls onSignedOut once and rejects with ApiError', async () => {
    const { client, tokens, onSignedOut } = setup((c) =>
      isRefresh(c) ? reply(401, errorBody(401, 'INVALID_REFRESH_TOKEN')) : reply(401, errorBody(401, 'UNAUTHORIZED')));
    const results = await Promise.allSettled([client.request('GET', '/a'), client.request('GET', '/b')]);
    expect(results.every((r) => r.status === 'rejected' && r.reason instanceof ApiError)).toBe(true);
    expect(tokens.current).toBeNull();
    expect(onSignedOut).toHaveBeenCalledTimes(1);
  });

  it('a retried request that 401s again does not refresh again', async () => {
    const { client, calls } = setup((c) => (isRefresh(c) ? reply(200, newPair) : reply(401, errorBody(401, 'UNAUTHORIZED'))));
    await expect(client.request('GET', '/api/copies')).rejects.toMatchObject({ status: 401 });
    expect(calls.filter(isRefresh)).toHaveLength(1);
    expect(calls).toHaveLength(3);
  });

  it('a non-JSON error body becomes an ApiError with the status', async () => {
    const { client } = setup(() => ({ status: 502, ok: false, text: async () => '<html>bad gateway</html>' }) as Response);
    await expect(client.request('GET', '/x')).rejects.toMatchObject({ status: 502, code: 'HTTP_502' });
  });

  it('a network failure rejects with a NETWORK ApiError', async () => {
    const { client } = setup(() => { throw new TypeError('Network request failed'); });
    await expect(client.request('GET', '/x')).rejects.toMatchObject({ status: 0, code: 'NETWORK' });
  });

  it('auth:false sends no Authorization header', async () => {
    const { client, calls } = setup(() => reply(200, {}), memoryTokens(null));
    await client.request('POST', '/api/auth/login', { body: {}, auth: false });
    expect(header(calls[0], 'Authorization')).toBeUndefined();
  });
});
