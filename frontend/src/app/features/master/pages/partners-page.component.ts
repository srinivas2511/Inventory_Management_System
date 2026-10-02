import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { catchError, of, switchMap } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { ToastService } from '../../../core/notification/toast.service';
import { HasPermissionDirective } from '../../../core/permission/has-permission.directive';
import { DialogService } from '../../../shared/dialogs/dialog.service';
import { problemOf } from '../../../shared/forms/server-errors';
import { Column, DataTableComponent } from '../../../shared/table/data-table.component';
import { FilterBarComponent, FilterDef } from '../../../shared/table/filter-bar.component';
import { listState } from '../../../shared/table/list-state';
import { PartnersApi } from '../api/partners.api';
import { PartnerFormDialogComponent } from '../dialogs/partner-form-dialog.component';
import { PartnerSummary } from '../models';

export type PartnerKind = 'suppliers' | 'customers';

/** One screen for both suppliers and customers; the route's {@code kind} data picks the API and the write permission. */
@Component({
  selector: 'app-partners-page',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    HasPermissionDirective,
    DataTableComponent,
    FilterBarComponent,
  ],
  template: `
    <header class="head">
      <h1>{{ plural() }}</h1>
      <button
        *appHasPermission="writePermission()"
        mat-flat-button
        color="primary"
        (click)="create()"
        data-testid="new"
      >
        <mat-icon>add</mat-icon> New {{ noun().toLowerCase() }}
      </button>
    </header>
    <app-filter-bar
      searchLabel="Search code, name, contact, e-mail or GST"
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
      [emptyText]="'No ' + plural().toLowerCase() + ' match the filters.'"
      (pageChange)="state.setPage($event.page, $event.size)"
      (sortChange)="state.setSort($event)"
    />
    <ng-template #rowActions let-row>
      <ng-container *appHasPermission="writePermission()">
        <button
          mat-icon-button
          [matMenuTriggerFor]="menu"
          [attr.aria-label]="'Actions for ' + row.code"
          data-testid="row-menu"
        >
          <mat-icon>more_vert</mat-icon>
        </button>
        <mat-menu #menu="matMenu">
          <button mat-menu-item (click)="edit(row)" data-testid="action-edit">Edit</button>
          @if (row.active) {
            <button mat-menu-item (click)="deactivate(row)" data-testid="action-deactivate">Deactivate</button>
          } @else {
            <button mat-menu-item (click)="activate(row)" data-testid="action-activate">Activate</button>
          }
        </mat-menu>
      </ng-container>
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
export class PartnersPageComponent {
  /** Set from the route's data ({@code withComponentInputBinding}). */
  readonly kind = input.required<PartnerKind>();

  private readonly http = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  private readonly dialogs = inject(DialogService);
  private readonly toast = inject(ToastService);

  protected readonly noun = computed(() => (this.kind() === 'suppliers' ? 'Supplier' : 'Customer'));
  protected readonly plural = computed(() => (this.kind() === 'suppliers' ? 'Suppliers' : 'Customers'));
  protected readonly writePermission = computed(() =>
    this.kind() === 'suppliers' ? 'SUPPLIER_MANAGE' : 'CUSTOMER_MANAGE',
  );
  private readonly api = computed(
    () => new PartnersApi(this.http, this.kind() === 'suppliers' ? '/suppliers' : '/customers'),
  );

  protected readonly state = listState({ sort: 'code,asc', filterKeys: ['active'] });
  protected readonly rows = signal<PartnerSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly error = signal('');
  private readonly refresh = signal(0);

  protected readonly filters: FilterDef[] = [
    {
      key: 'active',
      label: 'Status',
      options: [
        { value: 'true', label: 'Active' },
        { value: 'false', label: 'Inactive' },
      ],
    },
  ];

  protected readonly columns: Column<PartnerSummary>[] = [
    { key: 'code', header: 'Code', sortKey: 'code', value: (p) => p.code },
    { key: 'name', header: 'Name', sortKey: 'name', value: (p) => p.name },
    { key: 'contact', header: 'Contact', value: (p) => p.contactPerson },
    { key: 'phone', header: 'Phone', value: (p) => p.phone },
    { key: 'email', header: 'E-mail', sortKey: 'email', value: (p) => p.email },
    { key: 'gst', header: 'GST number', sortKey: 'gstNumber', value: (p) => p.gstNumber },
    { key: 'status', header: 'Status', sortKey: 'active', badge: (p) => (p.active ? 'ACTIVE' : 'INACTIVE') },
  ];

  private readonly query = computed(() => ({ params: this.state.params(), tick: this.refresh(), api: this.api() }));

  constructor() {
    toObservable(this.query)
      .pipe(
        switchMap(({ params, api }) =>
          api.list(params).pipe(
            catchError((e: unknown) => {
              this.error.set(problemOf(e)?.detail ?? `The ${this.plural().toLowerCase()} could not be loaded.`);
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

  protected create(): void {
    this.dialog
      .open(PartnerFormDialogComponent, { data: { api: this.api(), noun: this.noun() }, width: '640px' })
      .afterClosed()
      .subscribe((saved) => saved && this.refresh.update((n) => n + 1));
  }

  protected edit(row: PartnerSummary): void {
    this.api()
      .get(row.id)
      .subscribe({
        next: (partner) =>
          this.dialog
            .open(PartnerFormDialogComponent, { data: { api: this.api(), noun: this.noun(), partner }, width: '640px' })
            .afterClosed()
            .subscribe((saved) => saved && this.refresh.update((n) => n + 1)),
        error: (e: unknown) =>
          this.toast.error(problemOf(e)?.detail ?? `The ${this.noun().toLowerCase()} could not be loaded.`),
      });
  }

  protected activate(row: PartnerSummary): void {
    this.api()
      .activate(row.id)
      .subscribe({
        next: () => {
          this.toast.success(`${row.code} activated.`);
          this.refresh.update((n) => n + 1);
        },
        error: (e: unknown) => this.toast.error(problemOf(e)?.detail ?? 'It could not be activated.'),
      });
  }

  protected async deactivate(row: PartnerSummary): Promise<void> {
    const reason = await this.dialogs.askReason({
      title: `Deactivate ${row.code}?`,
      message: 'It can no longer be chosen on new documents. Existing records are kept.',
      confirmLabel: 'Deactivate',
      danger: true,
      required: false,
    });
    if (reason === null) {
      return;
    }
    this.api()
      .deactivate(row.id, reason)
      .subscribe({
        next: () => {
          this.toast.success(`${row.code} deactivated.`);
          this.refresh.update((n) => n + 1);
        },
        error: (e: unknown) => this.toast.error(problemOf(e)?.detail ?? 'It could not be deactivated.'),
      });
  }
}
