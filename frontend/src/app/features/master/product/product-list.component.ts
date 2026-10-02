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
import { ProductApi, ProductDto, SpringType, ProductStatus } from './product.api';
import { ProductFormComponent } from './product-form.component';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatTableModule, MatPaginatorModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatProgressSpinnerModule, MatTooltipModule,
    MatDialogModule, StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Spring Products</h2>
      <button mat-flat-button color="primary" (click)="openForm()" data-testid="add-product">
        <mat-icon>add</mat-icon> New Product
      </button>
    </div>
    <div class="filter-bar">
      <mat-form-field appearance="outline" class="search-field">
        <mat-label>Search</mat-label>
        <input matInput [formControl]="searchCtrl" data-testid="search" />
        <mat-icon matSuffix>search</mat-icon>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Spring type</mat-label>
        <mat-select [formControl]="typeCtrl">
          <mat-option [value]="null">All types</mat-option>
          @for (t of springTypes; track t) { <mat-option [value]="t">{{ t }}</mat-option> }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="DRAFT">Draft</mat-option>
          <mat-option value="ACTIVE">Active</mat-option>
          <mat-option value="OBSOLETE">Obsolete</mat-option>
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
        <ng-container matColumnDef="springType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">{{ r.springType }}</td>
        </ng-container>
        <ng-container matColumnDef="drawing">
          <th mat-header-cell *matHeaderCellDef>Drawing</th>
          <td mat-cell *matCellDef="let r">{{ r.drawingNumber ?? '—' }}{{ r.drawingRevision ? ' Rev ' + r.drawingRevision : '' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button mat-icon-button matTooltip="Edit" (click)="openForm(r)" [disabled]="r.status === 'OBSOLETE'">
              <mat-icon>edit</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns;"></tr>
      </table>
      <mat-paginator [length]="total()" [pageSize]="pageSize" [pageIndex]="pageIndex()"
        [pageSizeOptions]="[20,50]" (page)="onPage($event)" showFirstLastButtons />
    }
  `,
  styles: [`.page-header{display:flex;justify-content:space-between;align-items:center;padding:16px 16px 0}
    .filter-bar{display:flex;gap:12px;padding:8px 16px;flex-wrap:wrap}.search-field{flex:1;min-width:200px}
    .loading-center{display:flex;justify-content:center;padding:48px}table{width:100%}`],
})
export class ProductListComponent implements OnInit {
  protected readonly springTypes: SpringType[] = ['COMPRESSION', 'EXTENSION', 'TORSION', 'CONICAL', 'DISC_BELLEVILLE', 'WIRE_FORM', 'CUSTOM'];
  protected readonly columns = ['code', 'name', 'springType', 'drawing', 'status', 'actions'];
  protected readonly rows = signal<ProductDto[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = 20;
  protected readonly searchCtrl = new FormControl('');
  protected readonly typeCtrl = new FormControl<SpringType | null>(null);
  protected readonly statusCtrl = new FormControl<ProductStatus | null>(null);
  private readonly api = inject(ProductApi);
  private readonly dialog = inject(MatDialog);

  ngOnInit(): void {
    this.load();
    this.searchCtrl.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => { this.pageIndex.set(0); this.load(); });
    this.typeCtrl.valueChanges.subscribe(() => { this.pageIndex.set(0); this.load(); });
    this.statusCtrl.valueChanges.subscribe(() => { this.pageIndex.set(0); this.load(); });
  }

  protected load(): void {
    this.loading.set(true);
    this.api.search(this.searchCtrl.value ?? '', this.typeCtrl.value, this.statusCtrl.value, this.pageIndex(), this.pageSize).subscribe({
      next: (p) => { this.rows.set(p.content); this.total.set(p.totalElements); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  protected onPage(e: PageEvent): void { this.pageIndex.set(e.pageIndex); this.load(); }

  protected openForm(product?: ProductDto): void {
    this.dialog.open(ProductFormComponent, { width: '700px', data: product, disableClose: true })
      .afterClosed().subscribe((saved) => { if (saved) this.load(); });
  }
}
