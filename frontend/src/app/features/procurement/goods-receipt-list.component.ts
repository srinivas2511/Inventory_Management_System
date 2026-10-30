import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';

import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { GoodsReceiptFormComponent } from './goods-receipt-form.component';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PagedList } from '../../shared/paged-list';
import { ListPaginatorComponent } from '../../shared/components/list-paginator.component';

export interface GoodsReceiptDto {
  id: number;
  grNumber: string;
  purchaseOrderId: number;
  poNumber: string;
  receiptDate: string;
  status: string;
  receivedBy: number;
  requiresApproval?: boolean;
}

export interface GrnPendingDto {
  grnId: number;
  grnNumber: string;
  supplierId: number;
  supplierName: string;
  receiptDate: string;
  status: string;
}

export interface GrnMismatchDto {
  grnId: number;
  grnNumber: string;
  invoiceId: number;
  invoiceNumber: string;
  invoiceValue: number;
  grnValue: number;
  mismatchAmount: number;
}

@Component({
  selector: 'app-goods-receipt-list',
  standalone: true,
  imports: [
    ListPaginatorComponent,
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTabsModule,
    MatTooltipModule,
    MatSnackBarModule,
    DecimalPipe,
    IstDatePipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Goods Receipts</h2>
      <div style="display:flex;gap:8px">
        <button mat-stroked-button (click)="openReports()">
          <mat-icon aria-hidden="true">bar_chart</mat-icon> Reports
        </button>
        <button mat-raised-button color="primary" *appHasPermission="'INVENTORY_RECEIVE'" (click)="receiveGoods()">
          <mat-icon aria-hidden="true">add</mat-icon> Receive Goods
        </button>
      </div>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>PO Number</mat-label>
        <input matInput [formControl]="poCtrl" placeholder="Filter by PO Number" />
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon aria-hidden="true">refresh</mat-icon> Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="grNumber">
          <th mat-header-cell *matHeaderCellDef>GR Number</th>
          <td mat-cell *matCellDef="let r">{{ r.grNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="poNumber">
          <th mat-header-cell *matHeaderCellDef>PO Number</th>
          <td mat-cell *matCellDef="let r">{{ r.poNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="receiptDate">
          <th mat-header-cell *matHeaderCellDef>Receipt Date</th>
          <td mat-cell *matCellDef="let r">{{ r.receiptDate | istDate: 'dd/MM/yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            @if (r.status === 'PENDING_APPROVAL') {
              <button mat-icon-button color="primary" matTooltip="Approve GR"
                      aria-label="Approve GR"
                      *appHasPermission="'INVENTORY_RECEIVE'"
                      (click)="approveGr(r)">
                <mat-icon aria-hidden="true">check_circle</mat-icon>
              </button>
            }
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let r; columns: columns"></tr>
      </table>
      <app-list-paginator [paged]="paged" (changed)="load()" />
    }

    <!-- GRN Reports Dialog -->
    @if (reportsOpen()) {
      <div class="dialog-backdrop" role="button" tabindex="0" aria-label="Close reports"
           (click)="reportsOpen.set(false)" (keydown.escape)="reportsOpen.set(false)">
        <div class="dialog-panel wide" role="presentation" (click)="$event.stopPropagation()">
          <h3>GRN Reports</h3>
          <mat-tab-group>
            <mat-tab label="Pending GRNs">
              @if (pendingLoading()) {
                <div style="padding:16px"><mat-spinner diameter="32" /></div>
              } @else if (pendingRows().length === 0) {
                <p style="padding:12px;color:#666">No pending GRNs.</p>
              } @else {
                <table mat-table [dataSource]="pendingRows()" style="width:100%">
                  <ng-container matColumnDef="grnNumber">
                    <th mat-header-cell *matHeaderCellDef>GR Number</th>
                    <td mat-cell *matCellDef="let r">{{ r.grnNumber }}</td>
                  </ng-container>
                  <ng-container matColumnDef="supplierName">
                    <th mat-header-cell *matHeaderCellDef>Supplier</th>
                    <td mat-cell *matCellDef="let r">{{ r.supplierName }}</td>
                  </ng-container>
                  <ng-container matColumnDef="receiptDate">
                    <th mat-header-cell *matHeaderCellDef>Date</th>
                    <td mat-cell *matCellDef="let r">{{ r.receiptDate | istDate: 'dd/MM/yyyy' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="status">
                    <th mat-header-cell *matHeaderCellDef>Status</th>
                    <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="pendingColumns"></tr>
                  <tr mat-row *matRowDef="let r; columns: pendingColumns"></tr>
                </table>
              }
            </mat-tab>
            <mat-tab label="Invoice Mismatch">
              @if (mismatchLoading()) {
                <div style="padding:16px"><mat-spinner diameter="32" /></div>
              } @else if (mismatchRows().length === 0) {
                <p style="padding:12px;color:#666">No mismatches found.</p>
              } @else {
                <table mat-table [dataSource]="mismatchRows()" style="width:100%">
                  <ng-container matColumnDef="grnNumber">
                    <th mat-header-cell *matHeaderCellDef>GR Number</th>
                    <td mat-cell *matCellDef="let r">{{ r.grnNumber }}</td>
                  </ng-container>
                  <ng-container matColumnDef="invoiceNumber">
                    <th mat-header-cell *matHeaderCellDef>Invoice</th>
                    <td mat-cell *matCellDef="let r">{{ r.invoiceNumber }}</td>
                  </ng-container>
                  <ng-container matColumnDef="grnValue">
                    <th mat-header-cell *matHeaderCellDef>GR Value</th>
                    <td mat-cell *matCellDef="let r">{{ r.grnValue | number: '1.2-2' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="invoiceValue">
                    <th mat-header-cell *matHeaderCellDef>Invoice Value</th>
                    <td mat-cell *matCellDef="let r">{{ r.invoiceValue | number: '1.2-2' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="mismatchAmount">
                    <th mat-header-cell *matHeaderCellDef>Mismatch</th>
                    <td mat-cell *matCellDef="let r">{{ r.mismatchAmount | number: '1.2-2' }}</td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="mismatchColumns"></tr>
                  <tr mat-row *matRowDef="let r; columns: mismatchColumns"></tr>
                </table>
              }
            </mat-tab>
          </mat-tab-group>
          <div style="display:flex;justify-content:flex-end;margin-top:16px">
            <button mat-button (click)="reportsOpen.set(false)">Close</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .dialog-backdrop {
      position: fixed; inset: 0; background: rgba(0,0,0,.4);
      display: flex; align-items: center; justify-content: center; z-index: 1000;
    }
    .dialog-panel { background: #fff; border-radius: 8px; padding: 24px; min-width: 480px; }
    .dialog-panel.wide { min-width: 720px; max-width: 95vw; }
  `],
})
export class GoodsReceiptListComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  columns = ['grNumber', 'poNumber', 'receiptDate', 'status', 'actions'];
  rows = signal<GoodsReceiptDto[]>([]);
  loading = signal(false);
  readonly paged = new PagedList();
  poCtrl = new FormControl<string>('');

  // Reports dialog
  reportsOpen = signal(false);
  pendingLoading = signal(false);
  mismatchLoading = signal(false);
  pendingRows = signal<GrnPendingDto[]>([]);
  mismatchRows = signal<GrnMismatchDto[]>([]);
  pendingColumns = ['grnNumber', 'supplierName', 'receiptDate', 'status'];
  mismatchColumns = ['grnNumber', 'invoiceNumber', 'grnValue', 'invoiceValue', 'mismatchAmount'];

  ngOnInit(): void {
    this.load();
  }

  receiveGoods(): void {
    this.dialog
      .open(GoodsReceiptFormComponent, { width: '960px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.snack.open('Goods receipt posted', 'OK', { duration: 3000 });
          this.load();
        }
      });
  }

  approveGr(gr: GoodsReceiptDto): void {
    this.http.post<GoodsReceiptDto>(`/api/procurement/goods-receipts/${gr.id}/approve`, {}).subscribe({
      next: () => {
        this.snack.open('Goods receipt approved and posted', 'OK', { duration: 3000 });
        this.load();
      },
      error: (e) => this.snack.open(e.error?.detail ?? 'Approval failed', 'Close', { duration: 4000 }),
    });
  }

  openReports(): void {
    this.reportsOpen.set(true);
    this.loadPending();
    this.loadMismatch();
  }

  private loadPending(): void {
    this.pendingLoading.set(true);
    this.http.get<GrnPendingDto[]>('/api/procurement/grn-reports/pending').subscribe({
      next: (rows) => { this.pendingRows.set(rows); this.pendingLoading.set(false); },
      error: () => this.pendingLoading.set(false),
    });
  }

  private loadMismatch(): void {
    this.mismatchLoading.set(true);
    this.http.get<GrnMismatchDto[]>('/api/procurement/grn-reports/invoice-mismatch').subscribe({
      next: (rows) => { this.mismatchRows.set(rows); this.mismatchLoading.set(false); },
      error: () => this.mismatchLoading.set(false),
    });
  }

  load(): void {
    this.loading.set(true);
    const po = (this.poCtrl.value ?? '').trim();
    this.http
      .get<{ content: GoodsReceiptDto[] }>('/api/procurement/goods-receipts', {
        params: this.paged.params(po ? { po } : {}),
      })
      .subscribe({
        next: (page) => {
          this.rows.set(this.paged.apply(page));
          this.loading.set(false);
        },
        error: () => {
          this.rows.set([]);
          this.loading.set(false);
        },
      });
  }
}
