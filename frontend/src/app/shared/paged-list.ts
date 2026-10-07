import { signal } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';

export interface PageOf<T> {
  content?: T[];
  totalElements?: number;
}

/**
 * Server-side paging state for a list screen: the current page, the page size and the total, plus the query
 * parameters the backend expects. Lists ask for the newest records first so that new documents are never hidden
 * behind the first page.
 */
export class PagedList {
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly total = signal(0);

  /** Page, size and a newest-first sort, merged with the screen's own filters. */
  params(filters: Record<string, string> = {}, sort = 'id,desc'): Record<string, string> {
    return { ...filters, page: String(this.pageIndex()), size: String(this.pageSize()), sort };
  }

  /** Stores the total from a page response and returns its rows. */
  apply<T>(page: PageOf<T> | null | undefined): T[] {
    const rows = page?.content ?? [];
    this.total.set(page?.totalElements ?? rows.length);
    return rows;
  }

  /** Back to the first page, for when a filter changes. */
  reset(): void {
    this.pageIndex.set(0);
  }

  /** Applies a paginator event; returns true when the page or size changed and the list must be reloaded. */
  change(event: PageEvent): boolean {
    const changed = event.pageIndex !== this.pageIndex() || event.pageSize !== this.pageSize();
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    return changed;
  }
}
