export type FieldError = { field: string; message: string };

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fieldErrors: FieldError[] = [],
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

const MESSAGES: Record<string, string> = {
  ALREADY_CONFIRMED: "You've already confirmed this exchange.",
  ACCOUNT_HAS_ACTIVE_EXCHANGE: 'Finish or cancel your accepted exchanges first.',
  INVALID_CREDENTIALS: 'Wrong email or password.',
  PROFILE_INCOMPLETE: 'Complete your profile (name, city and postal code) first.',
  BOOK_NOT_FOUND: "We couldn't find that book. You can enter its details yourself.",
  COPY_NOT_AVAILABLE: 'One of the books is no longer available.',
  COPY_RESERVED: "A book that's part of an accepted exchange can't be changed.",
  PROPOSAL_NOT_OPEN: 'This proposal is no longer open.',
  PROPOSAL_ACTION_NOT_ALLOWED: "You can't do that with this proposal.",
  CONCURRENT_UPDATE: 'Something changed while you were doing that. Please try again.',
  EMAIL_TAKEN: 'An account with this email already exists.',
  INVALID_ISBN: "That isn't a valid ISBN.",
  SELF_PROPOSAL: "You can't swap with yourself.",
  CATALOG_UNAVAILABLE: 'The book catalogue is unavailable right now. Try again, or enter the details yourself.',
  UNAUTHORIZED: 'Please sign in again.',
};

export function friendlyMessage(e: unknown): string {
  if (!(e instanceof ApiError)) return 'Something went wrong.';
  if (e.code === 'NETWORK') return "Can't reach the server. Check your connection.";
  if (e.code === 'VALIDATION_FAILED' && e.fieldErrors.length > 0) {
    return e.fieldErrors.map((f) => `${f.field}: ${f.message}`).join('. ');
  }
  return MESSAGES[e.code] ?? (e.message || 'Something went wrong.');
}
