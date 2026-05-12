import { SpanishPaginatorIntl } from './spanish-paginator-intl';

describe('SpanishPaginatorIntl', () => {
  let intl: SpanishPaginatorIntl;

  beforeEach(() => {
    intl = new SpanishPaginatorIntl();
  });

  it('uses Spanish paginator labels', () => {
    expect(intl.itemsPerPageLabel).toBe('');
    expect(intl.nextPageLabel).toBe('Página siguiente');
    expect(intl.previousPageLabel).toBe('Página anterior');
    expect(intl.firstPageLabel).toBe('Primera página');
    expect(intl.lastPageLabel).toBe('Última página');
  });

  it('formats an empty range in Spanish', () => {
    expect(intl.getRangeLabel(0, 10, 0)).toBe('0 de 0');
  });

  it('formats the first page range in Spanish', () => {
    expect(intl.getRangeLabel(0, 6, 6)).toBe('1 – 6 de 6');
  });

  it('formats subsequent page ranges in Spanish', () => {
    expect(intl.getRangeLabel(1, 10, 25)).toBe('11 – 20 de 25');
  });

  it('normalizes negative lengths', () => {
    expect(intl.getRangeLabel(0, 10, -5)).toBe('1 – 10 de 0');
  });
});
