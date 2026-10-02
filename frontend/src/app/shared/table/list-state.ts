import { computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';

/** What a list screen asks the server for; lives in the URL so a filtered view can be bookmarked or shared. */
export interface ListParams {
  q: string;
  page: number;
  size: number;
  /** {@code property,direction}, as the API expects. */
  sort: string;
  filters: Record<string, string>;
}

export interface ListStateOptions {
  sort: string;
  filterKeys?: string[];
  size?: number;
}

/**
 * Binds a list's search text, filters, sort and page to the URL query string. Call it in a component's field
 * initialiser (it uses {@code inject}). Changing the search, a filter or the sort goes back to the first page.
 */
export function listState(options: ListStateOptions) {
  const route = inject(ActivatedRoute);
  const router = inject(Router);
  const filterKeys = options.filterKeys ?? [];
  const defaultSize = options.size ?? 25;

  const query = toSignal(route.queryParamMap.pipe(map((m) => ({ keys: m.keys, get: (k: string) => m.get(k) }))), {
    requireSync: true,
  });

  const params = computed<ListParams>(() => {
    const q = query();
    const filters: Record<string, string> = {};
    for (const key of filterKeys) {
      const value = q.get(key);
      if (value) {
        filters[key] = value;
      }
    }
    const page = Number(q.get('page'));
    const size = Number(q.get('size'));
    return {
      q: q.get('q') ?? '',
      page: Number.isInteger(page) && page > 0 ? page : 0,
      size: [10, 25, 50, 100].includes(size) ? size : defaultSize,
      sort: q.get('sort') ?? options.sort,
      filters,
    };
  });

  function navigate(changes: Record<string, string | number | null>): void {
    void router.navigate([], {
      relativeTo: route,
      queryParams: changes,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  return {
    params,
    setSearch: (q: string) => navigate({ q: q || null, page: null }),
    setFilter: (key: string, value: string) => navigate({ [key]: value || null, page: null }),
    setSort: (sort: string) => navigate({ sort: sort === options.sort ? null : sort, page: null }),
    setPage: (page: number, size: number) => navigate({ page: page || null, size: size === defaultSize ? null : size }),
  };
}

export type ListState = ReturnType<typeof listState>;
