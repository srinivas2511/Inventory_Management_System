import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { catchError, of, switchMap } from 'rxjs';
import { ToastService } from '../../../core/notification/toast.service';
import { HasPermissionDirective } from '../../../core/permission/has-permission.directive';
import { DialogService } from '../../../shared/dialogs/dialog.service';
import { problemOf } from '../../../shared/forms/server-errors';
import { Column, DataTableComponent } from '../../../shared/table/data-table.component';
import { FilterBarComponent, FilterDef } from '../../../shared/table/filter-bar.component';
import { listState } from '../../../shared/table/list-state';
import { MaterialsApi } from '../api/materials.api';
import { MaterialFormDialogComponent } from '../dialogs/material-form-dialog.component';
import { MATERIAL_TYPES, MaterialSummary } from '../models';

/** Materials: search, filter, create, edit, activate and deactivate (deactivation of a material in use needs an override). */
@Component({
  selector: 'app-materials-page',
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
      <h1>Materials</h1>
      <button
        *appHasPermission="'MASTERDATA_MANAGE'"
        mat-flat-button
        color="primary"
        (click)="create()"
        data-testid="new"
      >
        <mat-icon>add</mat-icon> New material
      </button>
    </header>
    <app-filter-bar
      searchLabel="Search code, name or grade"
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
      emptyText="No materials match the filters."
      (pageChange)="state.setPage($event.page, $event.size)"
      (sortChange)="state.setSort($event)"
    />
    <ng-template #rowActions let-row>
      <ng-container *appHasPermission="'MASTERDATA_MANAGE'">
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
export class MaterialsPageComponent {
  private readonly api = inject(MaterialsApi);
  private readonly dialog = inject(MatDialog);
  private readonly dialogs = inject(DialogService);
  private readonly toast = inject(ToastService);

  protected readonly state = listState({ sort: 'code,asc', filterKeys: ['active', 'materialType'] });
  protected readonly rows = signal<MaterialSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly error = signal('');
  private readonly refresh = signal(0);

  protected readonly filters: FilterDef[] = [
    { key: 'materialType', label: 'Type', options: MATERIAL_TYPES },
    {
      key: 'active',
      label: 'Status',
      options: [
        { value: 'true', label: 'Active' },
        { value: 'false', label: 'Inactive' },
      ],
    },
  ];

  protected readonly columns: Column<MaterialSummary>[] = [
    { key: 'code', header: 'Code', sortKey: 'code', value: (m) => m.code },
    { key: 'name', header: 'Name', sortKey: 'name', value: (m) => m.name },
    {
      key: 'type',
      header: 'Type',
      sortKey: 'materialType',
      value: (m) => MATERIAL_TYPES.find((t) => t.value === m.materialType)?.label,
    },
    { key: 'grade', header: 'Grade', sortKey: 'grade', value: (m) => m.grade },
    { key: 'diameter', header: 'Dia (mm)', sortKey: 'diameterMm', value: (m) => m.diameterMm },
    { key: 'uom', header: 'UOM', sortKey: 'uom', value: (m) => m.uom },
    { key: 'supplier', header: 'Preferred supplier', value: (m) => m.preferredSupplier?.name },
    { key: 'status', header: 'Status', sortKey: 'active', badge: (m) => (m.active ? 'ACTIVE' : 'INACTIVE') },
  ];

  private readonly query = computed(() => ({ params: this.state.params(), tick: this.refresh() }));

  constructor() {
    toObservable(this.query)
      .pipe(
        switchMap(({ params }) =>
          this.api.list(params).pipe(
            catchError((e: unknown) => {
              this.error.set(problemOf(e)?.detail ?? 'The materials could not be loaded.');
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
      .open(MaterialFormDialogComponent, { data: {}, width: '640px' })
      .afterClosed()
      .subscribe((saved) => saved && this.refresh.update((n) => n + 1));
  }

  protected edit(row: MaterialSummary): void {
    this.api.get(row.id).subscribe({
      next: (material) =>
        this.dialog
          .open(MaterialFormDialogComponent, { data: { material }, width: '640px' })
          .afterClosed()
          .subscribe((saved) => saved && this.refresh.update((n) => n + 1)),
      error: (e: unknown) => this.toast.error(problemOf(e)?.detail ?? 'The material could not be loaded.'),
    });
  }

  protected activate(row: MaterialSummary): void {
    this.api.activate(row.id).subscribe({
      next: () => {
        this.toast.success(`${row.code} activated.`);
        this.refresh.update((n) => n + 1);
      },
      error: (e: unknown) => this.toast.error(problemOf(e)?.detail ?? 'The material could not be activated.'),
    });
  }

  protected async deactivate(row: MaterialSummary): Promise<void> {
    const reason = await this.dialogs.askReason({
      title: `Deactivate ${row.code}?`,
      message: 'It can no longer be used in new documents. Existing records are kept.',
      confirmLabel: 'Deactivate',
      danger: true,
      required: false,
    });
    if (reason === null) {
      return;
    }
    this.api.deactivate(row.id, false, reason).subscribe({
      next: () => this.deactivated(row),
      error: (e: unknown) => {
        const problem = problemOf(e);
        if (problem?.code === 'DEACTIVATION_BLOCKED') {
          void this.forceDeactivate(row, reason, problem.detail);
        } else {
          this.toast.error(problem?.detail ?? 'The material could not be deactivated.');
        }
      },
    });
  }

  private async forceDeactivate(row: MaterialSummary, reason: string, detail?: string): Promise<void> {
    const confirmed = await this.dialogs.confirm({
      title: `${row.code} is still in use`,
      message: `${detail ?? 'It is referenced by open work.'} Deactivate it anyway? Open documents keep working; new ones cannot use it.`,
      confirmLabel: 'Deactivate anyway',
      danger: true,
    });
    if (confirmed) {
      this.api.deactivate(row.id, true, reason).subscribe({
        next: () => this.deactivated(row),
        error: (e: unknown) => this.toast.error(problemOf(e)?.detail ?? 'The material could not be deactivated.'),
      });
    }
  }

  private deactivated(row: MaterialSummary): void {
    this.toast.success(`${row.code} deactivated.`);
    this.refresh.update((n) => n + 1);
  }
}
