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
