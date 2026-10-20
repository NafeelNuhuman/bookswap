import { normalizeIsbn } from '../src/logic/isbn';

describe('normalizeIsbn', () => {
  it('strips hyphens and spaces from a valid ISBN-13', () => {
    expect(normalizeIsbn('978-0-441-01359-3')).toBe('9780441013593');
    expect(normalizeIsbn(' 9780441013593 ')).toBe('9780441013593');
  });

  it('rejects a bad check digit', () => {
    expect(normalizeIsbn('9780441013594')).toBeNull();
  });

  it('accepts a valid ISBN-10, including a final X', () => {
    expect(normalizeIsbn('0441013597')).toBe('0441013597');
    expect(normalizeIsbn('080442957X')).toBe('080442957X');
  });

  it('rejects a UPC/EAN that is not a book number', () => {
    expect(normalizeIsbn('012345678905')).toBeNull();
    expect(normalizeIsbn('5901234123457')).toBeNull();
  });

  it('rejects empty and non-numeric input', () => {
    expect(normalizeIsbn('')).toBeNull();
    expect(normalizeIsbn('   ')).toBeNull();
    expect(normalizeIsbn('hello')).toBeNull();
  });
});
