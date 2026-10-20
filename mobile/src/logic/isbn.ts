/**
 * Cleans a typed or scanned code into an ISBN the backend will accept, or returns null.
 * ISBN-13 must start 978/979 (other EAN codes are not books); ISBN-10 may end in X.
 */
export function normalizeIsbn(raw: string): string | null {
  const s = raw.replace(/[\s-]/g, '').toUpperCase();
  if (/^\d{13}$/.test(s)) {
    if (!/^97[89]/.test(s)) return null;
    const sum = [...s].reduce((acc, ch, i) => acc + Number(ch) * (i % 2 === 0 ? 1 : 3), 0);
    return sum % 10 === 0 ? s : null;
  }
  if (/^\d{9}[\dX]$/.test(s)) {
    const sum = [...s].reduce((acc, ch, i) => acc + (ch === 'X' ? 10 : Number(ch)) * (10 - i), 0);
    return sum % 11 === 0 ? s : null;
  }
  return null;
}
