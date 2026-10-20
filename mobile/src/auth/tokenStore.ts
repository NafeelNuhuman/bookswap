import * as SecureStore from 'expo-secure-store';
import type { TokenPair, TokenStore } from '../api/client';

const ACCESS = 'bookswap.access';
const REFRESH = 'bookswap.refresh';

/** Tokens live only in the platform's secure storage. */
export const secureTokenStore: TokenStore = {
  async get(): Promise<TokenPair | null> {
    const [access, refresh] = await Promise.all([
      SecureStore.getItemAsync(ACCESS),
      SecureStore.getItemAsync(REFRESH),
    ]);
    return access && refresh ? { access, refresh } : null;
  },
  async set(tokens: TokenPair) {
    await Promise.all([
      SecureStore.setItemAsync(ACCESS, tokens.access),
      SecureStore.setItemAsync(REFRESH, tokens.refresh),
    ]);
  },
  async clear() {
    await Promise.all([SecureStore.deleteItemAsync(ACCESS), SecureStore.deleteItemAsync(REFRESH)]);
  },
};
