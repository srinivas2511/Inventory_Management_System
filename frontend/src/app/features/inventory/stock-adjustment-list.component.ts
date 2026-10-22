import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DecimalPipe } from '@angular/common';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { LookupApi, LookupItem, LookupLocation, LookupOption } from '../../shared/lookup.api';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { InventoryApi, StockAdjustmentDto } from './inventory.api';
import { Page } from '../master/material/material.api';

@Component({
  selector: 'app-stock-adjustment-list',
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
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatDialogModule,
    IstDatePipe,
    DecimalPipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Stock Adjustments</h2>
      <button
        mat-flat-button
        color="primary"
        *appHasPermission="'INVENTORY_ADJUST'"
        (click)="openRequestDialog()"
        data-testid="request-adjustment"
      >
        <mat-icon>tune</mat-icon> Request Adjustment
      </button>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="REQUESTED">Requested</mat-option>
          <mat-option value="POSTED">Posted</mat-option>
          <mat-option value="REJECTED">Rejected</mat-option>
        </mat-select>
      </mat-form-field>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="page()?.content ?? []" class="mat-elevation-z1">
        <ng-container matColumnDef="adjNumber">
          <th mat-header-cell *matHeaderCellDef>Adj #</th>
          <td mat-cell *matCellDef="let r">{{ r.adjustmentNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="date">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let r">{{ r.createdAt | istDate: 'dd/MM/yy HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="item">
          <th mat-header-cell *matHeaderCellDef>Item</th>
          <td mat-cell *matCellDef="let r">{{ r.materialId ?? r.productId }}</td>
        </ng-container>
        <ng-container matColumnDef="variance">
          <th mat-header-cell *matHeaderCellDef>Variance</th>
          <td mat-cell *matCellDef="let r" [class.negative]="r.varianceQty < 0">
            {{ r.varianceQty | number: '1.3-3' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="varianceValue">
          <th mat-header-cell *matHeaderCellDef>Value</th>
          <td mat-cell *matCellDef="let r">₹{{ r.varianceValue | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">
            <app-status-badge [status]="r.status" />
          </td>
        </ng-container>
        <ng-container matColumnDef="reason">
          <th mat-header-cell *matHeaderCellDef>Reason</th>
          <td mat-cell *matCellDef="let r" class="truncate" [matTooltip]="r.reason">{{ r.reason }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            @if (r.status === 'REQUESTED') {
              <button
                mat-icon-button
                color="primary"
                matTooltip="Approve / Review"
                aria-label="Approve / Review"
                *appHasPermission="'ADJUST_REVIEW'"
                (click)="openApprovalDialog(r, 'approve')"
              >
                <mat-icon>check_circle</mat-icon>
              </button>
              <button
                mat-icon-button
                color="warn"
                matTooltip="Reject"
                aria-label="Reject"
                *appHasPermission="'ADJUST_REVIEW'"
                (click)="openApprovalDialog(r, 'reject')"
              >
                <mat-icon>cancel</mat-icon>
              </button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let row; columns: cols"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell no-data" [attr.colspan]="cols.length">No adjustments found.</td>
        </tr>
      </table>

      <mat-paginator
        [length]="page()?.totalElements ?? 0"
        [pageSize]="pageSize"
        [pageSizeOptions]="[20, 50]"
        (page)="onPage($event)"
      />
    }
  `,
  styles: [
    '.negative { color: var(--mat-warn-color, red); } .truncate { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }',
  ],
})
export class StockAdjustmentListComponent implements OnInit {
  private readonly api = inject(InventoryApi);
  private readonly dialog = inject(MatDialog);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly page = signal<Page<StockAdjustmentDto> | null>(null);
  readonly statusCtrl = new FormControl<string | null>(null);
  readonly cols = ['adjNumber', 'date', 'item', 'variance', 'varianceValue', 'status', 'reason', 'actions'];
  pageIndex = 0;
  pageSize = 20;

  ngOnInit(): void {
    this.statusCtrl.valueChanges.subscribe(() => {
      this.pageIndex = 0;
      this.load();
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getAdjustments(this.statusCtrl.value ?? undefined, undefined, this.pageIndex, this.pageSize).subscribe({
      next: (data) => {
        this.page.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }

  openRequestDialog(): void {
    const form = this.fb.group({
      itemType: ['MATERIAL', Validators.required],
      materialId: [null],
      productId: [null],
      warehouseId: [null, Validators.required],
      locationId: [null, Validators.required],
      stockStatus: ['AVAILABLE', Validators.required],
      countedQty: [null, [Validators.required, Validators.min(0)]],
      reason: ['', Validators.required],
    });
    this.dialog
      .open(AdjustmentFormDialogComponent, { data: { form }, width: '480px' })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.api.requestAdjustment(result).subscribe(() => this.load());
        }
      });
  }

  openApprovalDialog(adj: StockAdjustmentDto, action: 'approve' | 'reject'): void {
    const form = this.fb.group({ comment: ['', Validators.required] });
    this.dialog
      .open(ApprovalCommentDialogComponent, { data: { action, adjNumber: adj.adjustmentNumber, form }, width: '380px' })
      .afterClosed()
      .subscribe((result) => {
        if (!result) return;
        // Note: adj.id here is the adjustment ID; we'd need the approval request ID in a real flow.
        // For the prototype, pass adj.id as the approvalId — in production the pending step ID comes from the API.
        const call =
          action === 'approve'
            ? this.api.approveAdjustment(adj.id, result.comment)
            : this.api.rejectAdjustment(adj.id, result.comment);
        call.subscribe(() => this.load());
      });
  }
}

@Component({
  selector: 'app-adjustment-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>Request Stock Adjustment</h2>
    <mat-dialog-content [formGroup]="data.form" style="display:flex;flex-direction:column;gap:8px;padding-top:8px">
      <mat-form-field appearance="outline">
        <mat-label>Item Type</mat-label>
        <mat-select formControlName="itemType">
          <mat-option value="MATERIAL">Material</mat-option>
          <mat-option value="PRODUCT">Product</mat-option>
        </mat-select>
      </mat-form-field>
      @if (data.form.value.itemType === 'PRODUCT') {
        <mat-form-field appearance="outline">
          <mat-label>Product</mat-label>
          <mat-select formControlName="productId" data-testid="adj-product">
            @for (p of products(); track p.id) {
              <mat-option [value]="p.id">{{ p.code }} · {{ p.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      } @else {
        <mat-form-field appearance="outline">
          <mat-label>Material</mat-label>
          <mat-select formControlName="materialId" data-testid="adj-material">
            @for (m of materials(); track m.id) {
              <mat-option [value]="m.id">{{ m.code }} · {{ m.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      }
      <mat-form-field appearance="outline">
        <mat-label>Warehouse</mat-label>
        <mat-select formControlName="warehouseId" data-testid="adj-warehouse">
          @for (w of warehouses(); track w.id) {
            <mat-option [value]="w.id">{{ w.code }} · {{ w.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Location</mat-label>
        <mat-select formControlName="locationId" data-testid="adj-location">
          @for (l of locations(); track l.id) {
            <mat-option [value]="l.id">{{ l.code }} · {{ l.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Stock Status</mat-label>
        <mat-select formControlName="stockStatus">
          <mat-option value="AVAILABLE">Available</mat-option>
          <mat-option value="QUARANTINE">Quarantine</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Counted Qty</mat-label>
        <input matInput type="number" step="0.001" formControlName="countedQty" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Reason</mat-label>
        <input matInput formControlName="reason" />
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()">Cancel</button>
      <button mat-flat-button color="primary" [disabled]="data.form.invalid" (click)="ref.close(data.form.value)">
        Submit
      </button>
    </mat-dialog-actions>
  `,
})
export class AdjustmentFormDialogComponent implements OnInit {
  readonly data = inject(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<AdjustmentFormDialogComponent>);
  private readonly lookups = inject(LookupApi);

  readonly materials = signal<LookupItem[]>([]);
  readonly products = signal<LookupItem[]>([]);
  readonly warehouses = signal<LookupOption[]>([]);
  readonly locations = signal<LookupLocation[]>([]);

  ngOnInit(): void {
    const form = this.data.form as FormGroup;
    this.lookups.materials().subscribe((list) => this.materials.set(list));
    this.lookups.products().subscribe((list) => this.products.set(list));
    this.lookups.warehouses().subscribe((list) => this.warehouses.set(list));
    // Exactly one of material / product is needed, according to the item type.
    const requireItem = (itemType: string | null): void => {
      const material = form.get('materialId')!;
      const product = form.get('productId')!;
      material.setValidators(itemType === 'PRODUCT' ? [] : [Validators.required]);
      product.setValidators(itemType === 'PRODUCT' ? [Validators.required] : []);
      (itemType === 'PRODUCT' ? material : product).reset(null);
      material.updateValueAndValidity();
      product.updateValueAndValidity();
    };
    requireItem(form.get('itemType')!.value);
    form.get('itemType')!.valueChanges.subscribe(requireItem);
    form.get('warehouseId')!.valueChanges.subscribe((warehouseId) => {
      form.get('locationId')!.reset(null);
      this.locations.set([]);
      if (warehouseId) {
        this.lookups.locations(warehouseId).subscribe((list) => this.locations.set(list));
      }
    });
  }
}

@Component({
  selector: 'app-approval-comment-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ data.action === 'approve' ? 'Approve' : 'Reject' }} — {{ data.adjNumber }}</h2>
    <mat-dialog-content [formGroup]="data.form">
      <mat-form-field appearance="outline" style="width:100%;margin-top:8px">
        <mat-label>Comment</mat-label>
        <textarea matInput formControlName="comment" rows="3"></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()">Cancel</button>
      <button
        mat-flat-button
        [color]="data.action === 'approve' ? 'primary' : 'warn'"
        [disabled]="data.form.invalid"
        (click)="ref.close(data.form.value)"
      >
        {{ data.action === 'approve' ? 'Approve' : 'Reject' }}
      </button>
    </mat-dialog-actions>
  `,
})
export class ApprovalCommentDialogComponent {
  readonly data = inject(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<ApprovalCommentDialogComponent>);
}
