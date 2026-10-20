import { ApiError, friendlyMessage } from '../src/api/errors';

const err = (code: string, message = 'server said so', fieldErrors: { field: string; message: string }[] = []) =>
  new ApiError(400, code, message, fieldErrors);

describe('friendlyMessage', () => {
  it.each([
    'ALREADY_CONFIRMED',
    'ACCOUNT_HAS_ACTIVE_EXCHANGE',
    'INVALID_CREDENTIALS',
    'PROFILE_INCOMPLETE',
    'BOOK_NOT_FOUND',
    'COPY_NOT_AVAILABLE',
    'COPY_RESERVED',
    'PROPOSAL_NOT_OPEN',
    'PROPOSAL_ACTION_NOT_ALLOWED',
    'CONCURRENT_UPDATE',
    'EMAIL_TAKEN',
  ])('maps %s to a plain sentence, not the server message', (code) => {
    const text = friendlyMessage(err(code));
    expect(text.length).toBeGreaterThan(0);
    expect(text).not.toBe('server said so');
  });

  it('joins field errors for VALIDATION_FAILED', () => {
    const e = err('VALIDATION_FAILED', 'Validation failed', [
      { field: 'city', message: 'must not be blank' },
      { field: 'displayName', message: 'size must be between 0 and 60' },
    ]);
    expect(friendlyMessage(e)).toBe('city: must not be blank. displayName: size must be between 0 and 60');
  });

  it('falls back to the server message for an unknown code', () => {
    expect(friendlyMessage(err('SOMETHING_NEW', 'Specific problem'))).toBe('Specific problem');
  });

  it('describes a network failure', () => {
    expect(friendlyMessage(new ApiError(0, 'NETWORK', 'x', []))).toBe("Can't reach the server. Check your connection.");
  });

  it('handles non-ApiError values', () => {
    expect(friendlyMessage(new Error('boom'))).toBe('Something went wrong.');
    expect(friendlyMessage(undefined)).toBe('Something went wrong.');
  });
});
