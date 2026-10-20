import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { BookCard } from '../src/components/BookCard';
import { BookCover } from '../src/components/BookCover';
import { BookGrid } from '../src/components/BookGrid';
import { gradeLabel, ownerLine, statusLabel } from '../src/logic/format';

describe('BookCard', () => {
  it('shows title, author, the grade label and the status label', async () => {
    await render(
      <BookCard title="Dune" author="Frank Herbert" coverUrl="http://x/dune.jpg" grade="LIKE_NEW" status="AVAILABLE" onPress={() => undefined} />,
    );
    expect(screen.getByText('Dune')).toBeTruthy();
    expect(screen.getByText('Frank Herbert')).toBeTruthy();
    expect(screen.getByText('Like New')).toBeTruthy();
    expect(screen.getByText('Available')).toBeTruthy();
  });

  it('shows a subtitle when given and no status when there is none', async () => {
    await render(
      <BookCard title="Emma" author={null} coverUrl="http://x/c.jpg" grade="GOOD" subtitle="Sam · Leeds" onPress={() => undefined} />,
    );
    expect(screen.getByText('Sam · Leeds')).toBeTruthy();
    expect(screen.queryByText('Available')).toBeNull();
  });
});

describe('BookCover', () => {
  it('with a null coverUrl renders the title tile and does not crash', async () => {
    await render(<BookCover coverUrl={null} title="Ulysses" />);
    expect(screen.getByText('Ulysses')).toBeTruthy();
  });
});

describe('format helpers', () => {
  it('labels every grade and status', () => {
    expect(gradeLabel('NEW')).toBe('New');
    expect(gradeLabel('ACCEPTABLE')).toBe('Acceptable');
    expect(gradeLabel('POOR')).toBe('Poor');
    expect(statusLabel('UNLISTED')).toBe('Unlisted');
    expect(statusLabel('RESERVED')).toBe('Reserved');
  });

  it('ownerLine shows the owner name only when the city is null or blank', () => {
    expect(ownerLine({ displayName: 'Sam', city: 'Leeds' })).toBe('Sam · Leeds');
    expect(ownerLine({ displayName: 'Sam', city: null })).toBe('Sam');
    expect(ownerLine({ displayName: 'Sam', city: '  ' })).toBe('Sam');
  });
});

describe('BookGrid', () => {
  it('zero results shows the empty text passed in', async () => {
    await render(
      <BookGrid
        items={[]}
        keyOf={(x: { id: string }) => x.id}
        renderItem={() => null}
        emptyText="Nothing here yet"
        isLoading={false}
        isError={false}
        error={null}
        hasNextPage={false}
        onEndReached={() => undefined}
        onRetry={() => undefined}
      />,
    );
    expect(screen.getByText('Nothing here yet')).toBeTruthy();
  });

  it('an error shows the friendly message and a retry button', async () => {
    const { ApiError } = require('../src/api/errors');
    await render(
      <BookGrid
        items={[]}
        keyOf={(x: { id: string }) => x.id}
        renderItem={() => null}
        emptyText="Nothing"
        isLoading={false}
        isError
        error={new ApiError(0, 'NETWORK', 'x')}
        hasNextPage={false}
        onEndReached={() => undefined}
        onRetry={() => undefined}
      />,
    );
    expect(screen.getByText("Can't reach the server. Check your connection.")).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeTruthy();
  });
});
