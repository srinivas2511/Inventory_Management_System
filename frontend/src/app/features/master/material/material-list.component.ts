import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { StatusBadgeComponent } from '../../../shared/components/status-badge.component';
import { MaterialApi, MaterialDto } from './material.api';
import { MaterialFormComponent } from './material-form.component';

@Component({
  selector: 'app-material-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatDialogModule,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Materials</h2>
      <button mat-flat-button color="primary" (click)="openForm()" data-testid="add-material">
        <mat-icon>add</mat-icon> New Material
      </button>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline" class="search-field">
        <mat-label>Search</mat-label>
        <input matInput [formControl]="searchCtrl" placeholder="Code, name, grade…" data-testid="search" />
        <mat-icon matSuffix>search</mat-icon>
      </mat-form-field>
      <mat-form-field appearance="outline" class="status-field">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="activeCtrl" data-testid="status-filter">
          <mat-option [value]="null">All</mat-option>
          <mat-option [value]="true">Active</mat-option>
          <mat-option [value]="false">Inactive</mat-option>
        </mat-select>
      </mat-form-field>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let r">{{ r.code }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let r">{{ r.name }}</td>
        </ng-container>
        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">{{ r.materialType }}</td>
        </ng-container>
        <ng-container matColumnDef="uom">
          <th mat-header-cell *matHeaderCellDef>UOM</th>
          <td mat-cell *matCellDef="let r">{{ r.uom }}</td>
        </ng-container>
        <ng-container matColumnDef="reorder">
          <th mat-header-cell *matHeaderCellDef>Reorder</th>
          <td mat-cell *matCellDef="let r">{{ r.reorderLevel ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">
            <app-status-badge [status]="r.active ? 'ACTIVE' : 'INACTIVE'" />
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button mat-icon-button matTooltip="Edit" (click)="openForm(r)" data-testid="edit-material">
              <mat-icon>edit</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns;"></tr>
      </table>
      <mat-paginator
        [length]="total()"
        [pageSize]="pageSize"
        [pageIndex]="pageIndex()"
        [pageSizeOptions]="[20, 50]"
        (page)="onPage($event)"
        showFirstLastButtons
      />
    }
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 16px 0; }
    .filter-bar { display: flex; gap: 12px; padding: 8px 16px; flex-wrap: wrap; }
    .search-field { flex: 1; min-width: 200px; }
    .status-field { width: 140px; }
    .loading-center { display: flex; justify-content: center; padding: 48px; }
    table { width: 100%; }
  `],
})
export class MaterialListComponent implements OnInit {
  protected readonly columns = ['code', 'name', 'type', 'uom', 'reorder', 'status', 'actions'];
  protected readonly rows = signal<MaterialDto[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = 20;

  protected readonly searchCtrl = new FormControl('');
  protected readonly activeCtrl = new FormControl<boolean | null>(true);

  private readonly api = inject(MaterialApi);
  private readonly dialog = inject(MatDialog);

  ngOnInit(): void {
    this.load();
    this.searchCtrl.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.pageIndex.set(0);
      this.load();
    });
    this.activeCtrl.valueChanges.subscribe(() => {
      this.pageIndex.set(0);
      this.load();
    });
  }

  protected load(): void {
    this.loading.set(true);
    this.api
      .search(this.searchCtrl.value ?? '', this.activeCtrl.value, this.pageIndex(), this.pageSize)
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.total.set(page.totalElements);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected onPage(e: PageEvent): void {
    this.pageIndex.set(e.pageIndex);
    this.load();
  }

  protected openForm(material?: MaterialDto): void {
    const ref = this.dialog.open(MaterialFormComponent, {
      width: '600px',
      data: material,
    });
    ref.afterClosed().subscribe((saved) => { if (saved) this.load(); });
  }
}
