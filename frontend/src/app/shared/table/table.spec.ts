import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { Column, DataTableComponent } from './data-table.component';
import { FilterBarComponent } from './filter-bar.component';
import { listState } from './list-state';
import { StatusBadgeComponent, statusStyle } from '../ui/status-badge.component';

interface Row {
  code: string;
  active: boolean;
}

@Component({
  standalone: true,
  imports: [DataTableComponent],
  template: `<app-data-table
    [columns]="columns"
    [rows]="rows()"
    [total]="total"
    [page]="1"
    [size]="10"
    sort="code,asc"
    (pageChange)="page = $event"
    (sortChange)="sort = $event"
  />`,
})
class TableHostComponent {
  readonly rows = signal<Row[]>([{ code: 'A-1', active: true }]);
  total = 41;
  page: { page: number; size: number } | null = null;
  sort: string | null = null;
  columns: Column<Row>[] = [
    { key: 'code', header: 'Code', sortKey: 'code', value: (r) => r.code },
    { key: 'status', header: 'Status', badge: (r) => (r.active ? 'ACTIVE' : 'INACTIVE') },
  ];
}

describe('DataTableComponent', () => {
  let fixture: ComponentFixture<TableHostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TableHostComponent],
      providers: [provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(TableHostComponent);
    fixture.detectChanges();
  });

  it('renders text and badge columns', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('A-1');
    expect(fixture.nativeElement.querySelector('app-status-badge')?.textContent).toContain('Active');
  });

  it('shows the empty message when there are no rows', () => {
    fixture.componentInstance.rows.set([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="empty"]')).not.toBeNull();
  });

  it('reports a sort change as property,direction', () => {
    (fixture.nativeElement.querySelector('th.mat-sort-header, th[mat-sort-header]') as HTMLElement).click();
    fixture.detectChanges();
    expect(fixture.componentInstance.sort).toBe('code,desc');
  });
});

describe('StatusBadgeComponent', () => {
  it('maps known statuses and falls back for unknown ones', () => {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentRef.setInput('status', 'OBSOLETE');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Obsolete');
    fixture.componentRef.setInput('status', 'WEIRD');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Weird');
  });
});

describe('statusStyle (DESIGN.md section 8.7)', () => {
  it('colours the documented statuses', () => {
    expect(statusStyle('ACTIVE').tone).toBe('green');
    expect(statusStyle('LOW_STOCK').tone).toBe('amber');
    expect(statusStyle('QUARANTINE').tone).toBe('orange');
    expect(statusStyle('REJECTED').tone).toBe('red');
    expect(statusStyle('IN_PRODUCTION').tone).toBe('blue');
    expect(statusStyle('CLOSED').tone).toBe('teal');
    expect(statusStyle('DRAFT').tone).toBe('grey');
    expect(statusStyle('PARTIALLY_RECEIVED').tone).toBe('amber');
  });

  it('adds the product and account states the table lacks, and never leaves a status unstyled', () => {
    expect(statusStyle('OBSOLETE').tone).toBe('amber');
    expect(statusStyle('INACTIVE').tone).toBe('grey');
    expect(statusStyle('SOMETHING_NEW').tone).toBe('grey');
    expect(statusStyle('IN PRODUCTION').label).toBe('In production');
  });
});

describe('FilterBarComponent', () => {
  it('debounces the search and reports filter changes', fakeAsync(() => {
    TestBed.configureTestingModule({ imports: [FilterBarComponent], providers: [provideNoopAnimations()] });
    const fixture = TestBed.createComponent(FilterBarComponent);
    fixture.componentRef.setInput('filters', [
      { key: 'status', label: 'Status', options: [{ value: 'A', label: 'A' }] },
    ]);
    fixture.detectChanges();
    const searches: string[] = [];
    fixture.componentInstance.searchChange.subscribe((s: string) => searches.push(s));
    const input = fixture.nativeElement.querySelector('[data-testid="search"]') as HTMLInputElement;
    input.value = 'abc';
    input.dispatchEvent(new Event('input'));
    tick(100);
    expect(searches).toEqual([]);
    tick(300);
    expect(searches).toEqual(['abc']);
  }));
});

describe('listState', () => {
  function setup(url: string) {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'list', children: [] }])] });
    const router = TestBed.inject(Router);
    return router.navigateByUrl(url).then(() => {
      const route = TestBed.inject(ActivatedRoute);
      return {
        router,
        state: TestBed.runInInjectionContext(() => listState({ sort: 'code,asc', filterKeys: ['status'] })),
        route,
      };
    });
  }

  it('reads search, filters, sort and page from the URL, with defaults', async () => {
    const { state } = await setup('/list?q=ab&status=ACTIVE&page=2&size=50&sort=name,desc');
    expect(state.params()).toEqual({ q: 'ab', page: 2, size: 50, sort: 'name,desc', filters: { status: 'ACTIVE' } });
  });

  it('falls back to defaults for missing or invalid values', async () => {
    const { state } = await setup('/list?page=-3&size=7');
    expect(state.params()).toEqual({ q: '', page: 0, size: 25, sort: 'code,asc', filters: {} });
  });

  it('goes back to the first page when the filter changes', async () => {
    const { state, router } = await setup('/list?page=3');
    state.setFilter('status', 'DRAFT');
    await new Promise((r) => setTimeout(r));
    expect(router.url).toBe('/list?status=DRAFT');
  });
});
