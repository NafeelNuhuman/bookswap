import type { Copy, CopyCard, GradeInfo, Page } from '../src/api/types';

export function page<T>(content: T[], p = 0, totalPages = 1): Page<T> {
  return { content, page: p, size: 20, totalElements: content.length, totalPages };
}

export function copy(over: Partial<Copy> = {}): Copy {
  return {
    id: 'c1', isbn: '9780441013593', title: 'Dune', author: 'Frank Herbert', coverUrl: 'http://x/dune.jpg',
    grade: 'GOOD', status: 'AVAILABLE', detailsSource: 'CATALOG',
    createdAt: '2026-10-01T10:00:00Z', updatedAt: '2026-10-01T10:00:00Z', ...over,
  };
}

export function card(over: Partial<CopyCard> = {}): CopyCard {
  return {
    copyId: 'k1', isbn: '9780441013593', title: 'Dune', author: 'Frank Herbert', coverUrl: 'http://x/dune.jpg',
    grade: 'GOOD', detailsSource: 'CATALOG', owner: { id: 'u2', displayName: 'Sam', city: 'Leeds' }, ...over,
  };
}

export const grades: GradeInfo[] = [
  { code: 'NEW', label: 'New', criteria: 'Unread, no marks, no wear' },
  { code: 'LIKE_NEW', label: 'Like New', criteria: 'Read once or twice' },
  { code: 'GOOD', label: 'Good', criteria: 'Light wear on cover or spine' },
  { code: 'ACCEPTABLE', label: 'Acceptable', criteria: 'Clear wear' },
  { code: 'POOR', label: 'Poor', criteria: 'Heavy wear or damage' },
];

export const apiError = (status: number, code: string, message = code) => ({ status, body: { status, code, message, fieldErrors: [] } });

import type { Item, Party, Proposal } from '../src/api/types';

export const party = (id: string, name: string, over: Partial<Party> = {}): Party => ({ id, displayName: name, city: 'Leeds', ...over });

export const item = (copyId: string, title: string, over: Partial<Item> = {}): Item => ({
  copyId, isbn: '9780441013593', title, author: 'Someone', coverUrl: `http://x/${copyId}.jpg`, detailsSource: 'CATALOG',
  gradeAtProposal: 'GOOD', ...over,
});

export function proposal(over: Partial<Proposal> = {}): Proposal {
  return {
    id: 'p1', status: 'PROPOSED', cancelCause: null, parentId: null, message: null, rejectReason: null,
    createdAt: '2026-10-01T10:00:00Z', expiresAt: '2026-10-15T10:00:00Z', acceptedAt: null, confirmBy: null, completedAt: null,
    confirmedByMe: null, confirmedByOther: null, pickupNote: null,
    proposer: party('u2', 'Sam'), recipient: party('me', 'Me'),
    offered: [item('k1', 'Dune')], requested: [item('m1', 'Mine One')], ...over,
  };
}
