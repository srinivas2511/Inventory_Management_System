import { PagedList } from './paged-list';

describe('PagedList', () => {
  it('asks for the newest records first, with the filters kept', () => {
    const paged = new PagedList();
    expect(paged.params({ status: 'DRAFT' })).toEqual({ status: 'DRAFT', page: '0', size: '20', sort: 'id,desc' });
  });

  it('keeps the total of the last page so the paginator can offer the older ones', () => {
    const paged = new PagedList();
    const rows = paged.apply({ content: [{ id: 1 }, { id: 2 }], totalElements: 46 });
    expect(rows.length).toBe(2);
    expect(paged.total()).toBe(46);
  });

  it('falls back to the row count when the backend sends no total', () => {
    const paged = new PagedList();
    paged.apply({ content: [1, 2, 3] });
    expect(paged.total()).toBe(3);
    expect(paged.apply(null)).toEqual([]);
    expect(paged.total()).toBe(0);
  });

  it('reports a reload only when the page or the size changed', () => {
    const paged = new PagedList();
    expect(paged.change({ pageIndex: 0, pageSize: 20, length: 46 })).toBe(false);
    expect(paged.change({ pageIndex: 2, pageSize: 20, length: 46 })).toBe(true);
    expect(paged.pageIndex()).toBe(2);
    expect(paged.change({ pageIndex: 0, pageSize: 50, length: 46 })).toBe(true);
    expect(paged.pageSize()).toBe(50);
  });

  it('goes back to the first page when a filter changes', () => {
    const paged = new PagedList();
    paged.change({ pageIndex: 3, pageSize: 20, length: 100 });
    paged.reset();
    expect(paged.pageIndex()).toBe(0);
  });
});
