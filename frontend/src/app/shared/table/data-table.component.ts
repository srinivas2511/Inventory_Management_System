import { NgTemplateOutlet } from '@angular/common';
import { Component, TemplateRef, computed, input, output } from '@angular/core';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../ui/status-badge.component';

export interface Column<T> {
  key: string;
  header: string;
  /** The server-side sort property; omit for a column that cannot be sorted. */
  sortKey?: string;
  value?: (row: T) => string | number | null | undefined;
  /** Renders the returned status as a {@link StatusBadgeComponent} instead of text. */
  badge?: (row: T) => string | null | undefined;
}

/**
 * A server-paged, server-sorted table driven by column definitions. The screen owns the data and the paging state
 * (see {@code listState}); this component only displays it and reports page and sort changes. Row actions are a
 * template the screen supplies, so each screen decides which actions to offer from {@code allowedActions} and the
 * user's permissions.
 */
@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [MatTableModule, MatSortModule, MatPaginatorModule, NgTemplateOutlet, StatusBadgeComponent],
  template: `
    <table
      mat-table
      [dataSource]="rows()"
      matSort
      [matSortActive]="sortActive()"
      [matSortDirection]="sortDirection()"
      (matSortChange)="onSort($event)"
      class="table"
    >
      @for (col of columns(); track col.key) {
        <ng-container [matColumnDef]="col.key">
          <th mat-header-cell *matHeaderCellDef [mat-sort-header]="col.sortKey ?? col.key" [disabled]="!col.sortKey">
            {{ col.header }}
          </th>
          <td mat-cell *matCellDef="let row">
            @if (col.badge) {
              @if (col.badge(row); as status) {
                <app-status-badge [status]="status" />
              }
            } @else {
              {{ col.value ? col.value(row) : '' }}
            }
          </td>
        </ng-container>
      }
      <ng-container matColumnDef="__actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let row" class="actions">
          <ng-container *ngTemplateOutlet="actions(); context: { $implicit: row }" />
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="displayed()"></tr>
      <tr mat-row *matRowDef="let row; columns: displayed()" data-testid="row"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell empty" [attr.colspan]="displayed().length" data-testid="empty">{{ emptyText() }}</td>
      </tr>
    </table>
    <mat-paginator
      [length]="total()"
      [pageSize]="size()"
      [pageIndex]="page()"
      [pageSizeOptions]="[10, 25, 50, 100]"
      (page)="onPage($event)"
    />
  `,
  styles: `
    .table {
      width: 100%;
    }
    .empty {
      padding: 24px;
      text-align: center;
    }
    .actions {
      text-align: right;
      white-space: nowrap;
    }
  `,
})
export class DataTableComponent<T> {
  readonly columns = input.required<Column<T>[]>();
  readonly rows = input<T[]>([]);
  readonly total = input(0);
  readonly page = input(0);
  readonly size = input(25);
  /** {@code property,direction} */
  readonly sort = input('');
  readonly actions = input<TemplateRef<{ $implicit: T }> | null>(null);
  readonly emptyText = input('Nothing matches the filters.');

  readonly pageChange = output<{ page: number; size: number }>();
  readonly sortChange = output<string>();

  protected readonly displayed = computed(() => [
    ...this.columns().map((c) => c.key),
    ...(this.actions() ? ['__actions'] : []),
  ]);
  protected readonly sortActive = computed(() => this.sort().split(',')[0]);
  protected readonly sortDirection = computed(() => (this.sort().split(',')[1] === 'desc' ? 'desc' : 'asc'));

  protected onSort(event: Sort): void {
    this.sortChange.emit(event.direction ? `${event.active},${event.direction}` : '');
  }

  protected onPage(event: PageEvent): void {
    this.pageChange.emit({ page: event.pageIndex, size: event.pageSize });
  }
}
