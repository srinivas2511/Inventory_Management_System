import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { WarehouseApi, WarehouseDto } from '../warehouse/warehouse.api';
import { BinAssignmentDto, BinDto, BinStockLineDto, WmsApi } from './wms.api';

@Component({
  selector: 'app-wms-bin-view',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Bin View</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Warehouse</mat-label>
        <mat-select [formControl]="warehouseCtrl" (selectionChange)="loadBins()" data-test="warehouse">
          @for (w of warehouses(); track w.id) {
            <mat-option [value]="w.id">{{ w.warehouseCode }} - {{ w.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Find bin</mat-label>
        <input matInput [formControl]="searchCtrl" (keyup.enter)="loadBins()" />
      </mat-form-field>
      <button mat-stroked-button (click)="loadBins()"><mat-icon>search</mat-icon> Search</button>
    </div>

    <table mat-table [dataSource]="bins()" class="mat-elevation-z1" data-test="bins">
      <ng-container matColumnDef="binCode">
        <th mat-header-cell *matHeaderCellDef>Bin</th>
        <td mat-cell *matCellDef="let b">{{ b.binCode }}</td>
      </ng-container>
      <ng-container matColumnDef="zoneCode">
        <th mat-header-cell *matHeaderCellDef>Zone</th>
        <td mat-cell *matCellDef="let b">{{ b.zoneCode }}</td>
      </ng-container>
      <ng-container matColumnDef="weight">
        <th mat-header-cell *matHeaderCellDef>Weight (kg)</th>
        <td mat-cell *matCellDef="let b">
          {{ b.currentWeightKg | number: '1.0-3' }}
          @if (b.maxWeightKg) {
            / {{ b.maxWeightKg | number: '1.0-3' }}
          }
        </td>
      </ng-container>
      <ng-container matColumnDef="occupied">
        <th mat-header-cell *matHeaderCellDef>Stock</th>
        <td mat-cell *matCellDef="let b">{{ b.occupied ? 'Occupied' : 'Empty' }}</td>
      </ng-container>
      <ng-container matColumnDef="active">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let b"><app-status-badge [status]="b.active ? 'ACTIVE' : 'INACTIVE'" /></td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="binColumns"></tr>
      <tr
        mat-row
        *matRowDef="let row; columns: binColumns"
        class="clickable"
        [class.selected]="selected()?.id === row.id"
        (click)="select(row)"
      ></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell" [attr.colspan]="binColumns.length" style="text-align:center;padding:1rem">
          Choose a warehouse to see its bins
        </td>
      </tr>
    </table>

    @if (selected(); as bin) {
      <h3 class="mat-h3">Stock in {{ bin.binCode }}</h3>
      <table mat-table [dataSource]="stockLines()" class="mat-elevation-z1" data-test="stock">
        <ng-container matColumnDef="item">
          <th mat-header-cell *matHeaderCellDef>Item</th>
          <td mat-cell *matCellDef="let s">{{ s.itemCode }} {{ s.itemName }}</td>
        </ng-container>
        <ng-container matColumnDef="batch">
          <th mat-header-cell *matHeaderCellDef>Batch</th>
          <td mat-cell *matCellDef="let s">{{ s.batchNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let s"><app-status-badge [status]="s.stockStatus" /></td>
        </ng-container>
        <ng-container matColumnDef="qty">
          <th mat-header-cell *matHeaderCellDef>On hand</th>
          <td mat-cell *matCellDef="let s">{{ s.qtyOnHand | number: '1.0-3' }} {{ s.uom }}</td>
        </ng-container>
        <ng-container matColumnDef="reserved">
          <th mat-header-cell *matHeaderCellDef>Reserved</th>
          <td mat-cell *matCellDef="let s">{{ s.qtyReserved | number: '1.0-3' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="stockColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: stockColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="stockColumns.length" style="text-align:center;padding:1rem">
            This bin is empty
          </td>
        </tr>
      </table>

      <h3 class="mat-h3">Fixed-bin items</h3>
      <form *appHasPermission="'BIN_MANAGE'" class="edit-form" (ngSubmit)="addAssignment()">
        <mat-form-field appearance="outline">
          <mat-label>Material id</mat-label>
          <input matInput type="number" min="1" [formControl]="materialIdCtrl" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Product id</mat-label>
          <input matInput type="number" min="1" [formControl]="productIdCtrl" />
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit"><mat-icon>add</mat-icon> Assign</button>
      </form>
      <table mat-table [dataSource]="assignments()" class="mat-elevation-z1" data-test="assignments">
        <ng-container matColumnDef="item">
          <th mat-header-cell *matHeaderCellDef>Item</th>
          <td mat-cell *matCellDef="let a">{{ a.itemCode }} {{ a.itemName }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let a">
            <button *appHasPermission="'BIN_MANAGE'" mat-button (click)="removeAssignment(a)">
              <mat-icon>delete</mat-icon> Remove
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="assignmentColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: assignmentColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="assignmentColumns.length" style="text-align:center;padding:1rem">
            No fixed items: put-away follows the strategy rules
          </td>
        </tr>
      </table>
    }
  `,
  styles: `
    .filter-bar,
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .clickable {
      cursor: pointer;
    }
    .selected {
      background: rgba(63, 81, 181, 0.08);
    }
  `,
})
export class WmsBinViewComponent implements OnInit {
  private api = inject(WmsApi);
  private warehouseApi = inject(WarehouseApi);
  private snack = inject(MatSnackBar);

  binColumns = ['binCode', 'zoneCode', 'weight', 'occupied', 'active'];
  stockColumns = ['item', 'batch', 'status', 'qty', 'reserved'];
  assignmentColumns = ['item', 'actions'];

  warehouses = signal<WarehouseDto[]>([]);
  bins = signal<BinDto[]>([]);
  selected = signal<BinDto | null>(null);
  stockLines = signal<BinStockLineDto[]>([]);
  assignments = signal<BinAssignmentDto[]>([]);

  warehouseCtrl = new FormControl<number | null>(null);
  searchCtrl = new FormControl('');
  materialIdCtrl = new FormControl<number | null>(null);
  productIdCtrl = new FormControl<number | null>(null);

  ngOnInit(): void {
    this.warehouseApi.list().subscribe({
      next: (ws) => {
        this.warehouses.set(ws.filter((w) => w.active));
        if (this.warehouses().length === 1) {
          this.warehouseCtrl.setValue(this.warehouses()[0].id);
          this.loadBins();
        }
      },
      error: () => this.snack.open('Failed to load warehouses', 'Dismiss', { duration: 3000 }),
    });
  }

  loadBins(): void {
    const wh = this.warehouseCtrl.value;
    if (!wh) return;
    this.selected.set(null);
    this.api.getBins(wh, null, this.searchCtrl.value || null).subscribe((p) => this.bins.set(p.content));
  }

  select(bin: BinDto): void {
    this.selected.set(bin);
    this.api.getBinStock(bin.id).subscribe((s) => this.stockLines.set(s));
    this.api.getAssignments(bin.id).subscribe((a) => this.assignments.set(a));
  }

  addAssignment(): void {
    const bin = this.selected();
    const material = this.materialIdCtrl.value;
    const product = this.productIdCtrl.value;
    if (!bin || (!material && !product) || (material && product)) {
      this.snack.open('Give either a material id or a product id', 'Dismiss', { duration: 3000 });
      return;
    }
    this.api.addAssignment(bin.id, material || null, product || null).subscribe({
      next: () => {
        this.materialIdCtrl.reset();
        this.productIdCtrl.reset();
        this.select(bin);
      },
      error: (e) => this.snack.open(e?.error?.detail ?? 'Could not assign the item', 'Dismiss', { duration: 5000 }),
    });
  }

  removeAssignment(a: BinAssignmentDto): void {
    const bin = this.selected();
    if (!bin) return;
    this.api.removeAssignment(bin.id, a.id).subscribe({ next: () => this.select(bin) });
  }
}
