import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { ToastService } from '../../../core/notification/toast.service';
import { HasPermissionDirective } from '../../../core/permission/has-permission.directive';
import { DialogService } from '../../../shared/dialogs/dialog.service';
import { problemOf } from '../../../shared/forms/server-errors';
import { Column, DataTableComponent } from '../../../shared/table/data-table.component';
import { FilterBarComponent, FilterDef } from '../../../shared/table/filter-bar.component';
import { listState } from '../../../shared/table/list-state';
import { ProductsApi } from '../api/products.api';
import { ProductSummary, SpringType } from '../models';

export const SPRING_TYPE_LABELS: Record<SpringType, string> = {
  COMPRESSION: 'Compression',
  EXTENSION: 'Extension / tension',
  TORSION: 'Torsion',
  CONICAL: 'Conical',
  DISC_BELLEVILLE: 'Disc / Belleville',
  WIRE_FORM: 'Wire form',
  CUSTOM: 'Custom',
};

/** Spring products: search, filter, and the lifecycle actions the server says the caller may take on each row. */
@Component({
  selector: 'app-products-page',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    RouterLink,
    HasPermissionDirective,
    DataTableComponent,
    FilterBarComponent,
  ],
  template: `
    <header class="head">
      <h1>Spring products</h1>
      <a
        *appHasPermission="'PRODUCT_CREATE'"
        mat-flat-button
        color="primary"
        routerLink="/master/products/new"
        data-testid="new"
      >
        <mat-icon>add</mat-icon> New product
      </a>
    </header>
    <app-filter-bar
      searchLabel="Search code, name or drawing number"
      [q]="state.params().q"
      [filters]="filters"
      [values]="state.params().filters"
      (searchChange)="state.setSearch($event)"
      (filterChange)="state.setFilter($event.key, $event.value)"
    />
    @if (error()) {
      <p class="error" role="alert" data-testid="list-error">{{ error() }}</p>
    }
    <app-data-table
      [columns]="columns"
      [rows]="rows()"
      [total]="total()"
      [page]="state.params().page"
      [size]="state.params().size"
      [sort]="state.params().sort"
      [actions]="rowActions"
      emptyText="No products match the filters."
      (pageChange)="state.setPage($event.page, $event.size)"
      (sortChange)="state.setSort($event)"
    />
    <ng-template #rowActions let-row>
      @if (row.allowedActions.length) {
        <button
          mat-icon-button
          [matMenuTriggerFor]="menu"
          [attr.aria-label]="'Actions for ' + row.code"
          data-testid="row-menu"
        >
          <mat-icon>more_vert</mat-icon>
        </button>
        <mat-menu #menu="matMenu">
          @if (row.allowedActions.includes('EDIT')) {
            <button mat-menu-item (click)="edit(row)" data-testid="action-edit">Edit</button>
          }
          @if (row.allowedActions.includes('ACTIVATE')) {
            <button mat-menu-item (click)="activate(row)" data-testid="action-activate">Activate</button>
          }
          @if (row.allowedActions.includes('OBSOLETE')) {
            <button mat-menu-item (click)="obsolete(row)" data-testid="action-obsolete">Make obsolete</button>
          }
        </mat-menu>
      }
    </ng-template>
  `,
  styles: `
    .head {
      align-items: center;
      display: flex;
      justify-content: space-between;
    }
    .error {
      color: #c62828;
    }
  `,
})
export class ProductsPageComponent {
  private readonly api = inject(ProductsApi);
  private readonly router = inject(Router);
  private readonly dialogs = inject(DialogService);
  private readonly toast = inject(ToastService);

  protected readonly state = listState({ sort: 'code,asc', filterKeys: ['springType', 'status'] });
  protected readonly rows = signal<ProductSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly error = signal('');
  private readonly refresh = signal(0);

  protected readonly filters: FilterDef[] = [
    {
      key: 'springType',
      label: 'Spring type',
      options: Object.entries(SPRING_TYPE_LABELS).map(([value, label]) => ({ value, label })),
    },
    {
      key: 'status',
      label: 'Status',
      options: [
        { value: 'DRAFT', label: 'Draft' },
        { value: 'ACTIVE', label: 'Active' },
        { value: 'OBSOLETE', label: 'Obsolete' },
      ],
    },
  ];

  protected readonly columns: Column<ProductSummary>[] = [
    { key: 'code', header: 'Code', sortKey: 'code', value: (p) => p.code },
    { key: 'name', header: 'Name', sortKey: 'name', value: (p) => p.name },
    { key: 'type', header: 'Type', sortKey: 'springType', value: (p) => SPRING_TYPE_LABELS[p.springType] },
    { key: 'wire', header: 'Wire Ø', value: (p) => p.wireDiameter },
    { key: 'od', header: 'Outer Ø', value: (p) => p.outerDiameter },
    { key: 'length', header: 'Free length', value: (p) => p.freeLength },
    {
      key: 'drawing',
      header: 'Drawing',
      value: (p) => (p.drawingNumber ? `${p.drawingNumber} ${p.drawingRevision ?? ''}`.trim() : ''),
    },
    { key: 'customer', header: 'Customer', value: (p) => p.customer?.name },
    { key: 'status', header: 'Status', sortKey: 'status', badge: (p) => p.status },
  ];

  private readonly query = computed(() => ({ params: this.state.params(), tick: this.refresh() }));

  constructor() {
    toObservable(this.query)
      .pipe(
        switchMap(({ params }) =>
          this.api.list(params).pipe(
            catchError((e: unknown) => {
              this.error.set(problemOf(e)?.detail ?? 'The products could not be loaded.');
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        if (page) {
          this.error.set('');
          this.rows.set(page.content);
          this.total.set(page.totalElements);
        }
      });
  }

  protected edit(row: ProductSummary): void {
    void this.router.navigate(['/master/products', row.id, 'edit']);
  }

  protected activate(row: ProductSummary): void {
    this.api.activate(row.id).subscribe({
      next: () => {
        this.toast.success(`${row.code} activated.`);
        this.refresh.update((n) => n + 1);
      },
      // the server lists every missing required attribute
      error: (e: unknown) => {
        const problem = problemOf(e);
        const missing = problem?.fieldErrors?.map((f) => f.message).join(' ');
        this.toast.error(
          missing
            ? `${row.code} cannot be activated yet. ${missing}`
            : (problem?.detail ?? 'The product could not be activated.'),
        );
      },
    });
  }

  protected async obsolete(row: ProductSummary): Promise<void> {
    const reason = await this.dialogs.askReason({
      title: `Make ${row.code} obsolete?`,
      message: 'It stays on record for traceability but can no longer be edited or newly used.',
      confirmLabel: 'Make obsolete',
      danger: true,
      required: false,
    });
    if (reason === null) {
      return;
    }
    this.api.obsolete(row.id, reason).subscribe({
      next: () => {
        this.toast.success(`${row.code} is now obsolete.`);
        this.refresh.update((n) => n + 1);
      },
      error: (e: unknown) => this.toast.error(problemOf(e)?.detail ?? 'The product could not be made obsolete.'),
    });
  }
}
