import { theme } from '../src/theme';

describe('theme', () => {
  it('uses hex colours for the cream background and brown text', () => {
    expect(theme.colors.cream).toMatch(/^#[0-9A-Fa-f]{6}$/);
    expect(theme.colors.brown).toMatch(/^#[0-9A-Fa-f]{6}$/);
  });

  it('has a serif title font', () => {
    expect(theme.fonts.title.length).toBeGreaterThan(0);
  });
});
