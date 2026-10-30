import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
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
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { PurchaseOrderFormComponent } from './purchase-order-form.component';
import { SessionStore } from '../../core/auth/session.store';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PagedList } from '../../shared/paged-list';
import { ListPaginatorComponent } from '../../shared/components/list-paginator.component';

export interface PurchaseOrderItemDto {
  id: number;
  lineNo: number;
  materialId: number;
  orderedQty: number;
  receivedQty: number;
  rate: number;
  taxPct: number;
  lineTotal: number;
}

export interface PurchaseOrderDto {
  id: number;
  poNumber: string;
  supplierId: number;
  supplierName: string;
  poDate: string;
  expectedDeliveryDate: string;
  status: string;
  grandTotal: number;
  currency: string;
  submittedBy?: number | null;
  items: PurchaseOrderItemDto[];
  allowedActions: string[];
}

export interface PoRevisionDto {
  id: number;
  poId: number;
  revisionNo: number;
  reason: string;
  createdBy: number | null;
  createdAt: string;
}

@Component({
  selector: 'app-purchase-order-list',
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
      <h2 class="mat-h2">Purchase Orders</h2>
      <button mat-raised-button color="primary" *appHasPermission="'PURCHASE_CREATE'" (click)="createPo()">
        <mat-icon>add</mat-icon> New PO
      </button>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="DRAFT">Draft</mat-option>
          <mat-option value="SUBMITTED">Submitted</mat-option>
          <mat-option value="APPROVED">Approved</mat-option>
          <mat-option value="SENT_TO_SUPPLIER">Sent to Supplier</mat-option>
          <mat-option value="PARTIALLY_RECEIVED">Partially Received</mat-option>
          <mat-option value="RECEIVED">Received</mat-option>
          <mat-option value="CANCELLED">Cancelled</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="poNumber">
          <th mat-header-cell *matHeaderCellDef>PO Number</th>
          <td mat-cell *matCellDef="let r">{{ r.poNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="supplierId">
          <th mat-header-cell *matHeaderCellDef>Supplier</th>
          <td mat-cell *matCellDef="let r">{{ r.supplierName }}</td>
        </ng-container>
        <ng-container matColumnDef="poDate">
          <th mat-header-cell *matHeaderCellDef>PO Date</th>
          <td mat-cell *matCellDef="let r">{{ r.poDate | istDate: 'dd/MM/yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="grandTotal">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let r">{{ r.grandTotal | number: '1.2-2' }} {{ r.currency }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            @if (r.allowedActions?.includes('submit')) {
              <button
                mat-icon-button
                matTooltip="Submit"
                aria-label="Submit"
                *appHasPermission="'PURCHASE_CREATE'"
                (click)="doAction(r.id, 'submit')"
              >
                <mat-icon>send</mat-icon>
              </button>
            }
            @if (r.allowedActions?.includes('approve')) {
              <span
                class="approve-wrap"
                *appHasPermission="'PURCHASE_APPROVE'"
                [matTooltip]="
                  ownSubmission(r) ? 'You submitted this order, so another approver must approve it' : 'Approve'
                "
              >
                <button
                  mat-icon-button
                  aria-label="Approve"
                  [disabled]="ownSubmission(r)"
                  (click)="doAction(r.id, 'approve')"
                >
                  <mat-icon>check_circle</mat-icon>
                </button>
              </span>
            }
            @if (r.allowedActions?.includes('send_to_supplier')) {
              <button
                mat-icon-button
                matTooltip="Send to Supplier"
                aria-label="Send to Supplier"
                *appHasPermission="'PURCHASE_APPROVE'"
                (click)="doAction(r.id, 'send-to-supplier')"
              >
                <mat-icon>local_shipping</mat-icon>
              </button>
            }
            @if (r.status === 'APPROVED' || r.status === 'SENT_TO_SUPPLIER') {
              <button
                mat-icon-button
                matTooltip="Amend (record revision)"
                aria-label="Amend"
                *appHasPermission="'PURCHASE_AMEND'"
                (click)="openAmendDialog(r)"
              >
                <mat-icon aria-hidden="true">edit_note</mat-icon>
              </button>
            }
            <button
              mat-icon-button
              matTooltip="Revision History"
              aria-label="Revision History"
              *appHasPermission="'PURCHASE_VIEW'"
              (click)="openRevisionsDialog(r)"
            >
              <mat-icon aria-hidden="true">history</mat-icon>
            </button>
            <a
              mat-icon-button
              [href]="'/api/procurement/purchase-orders/' + r.id + '/pdf'"
              target="_blank"
              aria-label="Download PDF"
              matTooltip="Download PDF"
            >
              <mat-icon aria-hidden="true">picture_as_pdf</mat-icon>
            </a>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let r; columns: columns"></tr>
      </table>
      <app-list-paginator [paged]="paged" (changed)="load()" />
    }

    <!-- Amend Dialog -->
    @if (amendDialogOpen()) {
      <div class="dialog-backdrop" role="button" tabindex="0" aria-label="Close dialog"
           (click)="closeAmendDialog()" (keydown.escape)="closeAmendDialog()">
        <div class="dialog-panel" role="presentation" (click)="$event.stopPropagation()">
          <h3>Amend PO — {{ amendTarget()?.poNumber }}</h3>
          <form [formGroup]="amendForm" (ngSubmit)="submitAmend()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Reason for Amendment</mat-label>
              <textarea matInput formControlName="reason" rows="3" placeholder="Describe the change..."></textarea>
              @if (amendForm.get('reason')?.hasError('required')) {
                <mat-error>Reason is required</mat-error>
              }
            </mat-form-field>
            <div class="dialog-actions">
              <button mat-button type="button" (click)="closeAmendDialog()">Cancel</button>
              <button mat-raised-button color="primary" type="submit" [disabled]="amendForm.invalid || savingAmend()">
                Save Revision
              </button>
            </div>
          </form>
        </div>
      </div>
    }

    <!-- Revisions Dialog -->
    @if (revisionsDialogOpen()) {
      <div class="dialog-backdrop" role="button" tabindex="0" aria-label="Close dialog"
           (click)="closeRevisionsDialog()" (keydown.escape)="closeRevisionsDialog()">
        <div class="dialog-panel wide" role="presentation" (click)="$event.stopPropagation()">
          <h3>Revision History — {{ amendTarget()?.poNumber }}</h3>
          @if (revisionsLoading()) {
            <mat-spinner diameter="32" />
          } @else if (revisions().length === 0) {
            <p style="color:#666; padding:12px 0">No amendments recorded.</p>
          } @else {
            <table mat-table [dataSource]="revisions()" class="full-width">
              <ng-container matColumnDef="revisionNo">
                <th mat-header-cell *matHeaderCellDef>#</th>
                <td mat-cell *matCellDef="let r">{{ r.revisionNo }}</td>
              </ng-container>
              <ng-container matColumnDef="reason">
                <th mat-header-cell *matHeaderCellDef>Reason</th>
                <td mat-cell *matCellDef="let r">{{ r.reason }}</td>
              </ng-container>
              <ng-container matColumnDef="createdBy">
                <th mat-header-cell *matHeaderCellDef>Changed By</th>
                <td mat-cell *matCellDef="let r">{{ r.createdBy ?? '—' }}</td>
              </ng-container>
              <ng-container matColumnDef="createdAt">
                <th mat-header-cell *matHeaderCellDef>Date</th>
                <td mat-cell *matCellDef="let r">{{ r.createdAt | istDate: 'dd/MM/yyyy HH:mm' }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="revisionColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: revisionColumns"></tr>
            </table>
          }
          <div class="dialog-actions">
            <button mat-button (click)="closeRevisionsDialog()">Close</button>
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
    .dialog-panel { background: #fff; border-radius: 8px; padding: 24px; width: 480px; }
    .dialog-panel.wide { width: 680px; }
    .dialog-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
    .full-width { width: 100%; }
  `],
})
export class PurchaseOrderListComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);
  private dialog = inject(MatDialog);
  private session = inject(SessionStore);
  private fb = inject(FormBuilder);

  columns = ['poNumber', 'supplierId', 'poDate', 'grandTotal', 'status', 'actions'];
  revisionColumns = ['revisionNo', 'reason', 'createdBy', 'createdAt'];
  rows = signal<PurchaseOrderDto[]>([]);
  loading = signal(false);
  readonly paged = new PagedList();
  statusCtrl = new FormControl<string | null>(null);

  // Amend dialog state
  amendDialogOpen = signal(false);
  amendTarget = signal<PurchaseOrderDto | null>(null);
  savingAmend = signal(false);
  amendForm!: FormGroup;

  // Revisions dialog state
  revisionsDialogOpen = signal(false);
  revisionsLoading = signal(false);
  revisions = signal<PoRevisionDto[]>([]);

  ngOnInit(): void {
    this.statusCtrl.valueChanges.subscribe(() => {
      this.paged.reset();
      this.load();
    });
    this.load();
  }

  ownSubmission(po: PurchaseOrderDto): boolean {
    const userId = this.session.user()?.id;
    return userId != null && po.submittedBy != null && po.submittedBy === userId;
  }

  createPo(): void {
    this.dialog
      .open(PurchaseOrderFormComponent, { width: '960px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.snack.open('Purchase order created', 'OK', { duration: 3000 });
          this.load();
        }
      });
  }

  load(): void {
    this.loading.set(true);
    const params: Record<string, string> = {};
    const s = this.statusCtrl.value;
    if (s) params['status'] = s;
    this.http
      .get<{ content: PurchaseOrderDto[] }>('/api/procurement/purchase-orders', { params: this.paged.params(params) })
      .subscribe({
        next: (page) => {
          this.rows.set(this.paged.apply(page));
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  doAction(id: number, action: string): void {
    this.http.post<unknown>(`/api/procurement/purchase-orders/${id}/${action}`, {}).subscribe({
      next: () => {
        this.snack.open('Action completed', 'OK', { duration: 2000 });
        this.load();
      },
      error: (e) => this.snack.open(e.error?.detail ?? 'Error', 'OK', { duration: 4000 }),
    });
  }

  openAmendDialog(po: PurchaseOrderDto): void {
    this.amendTarget.set(po);
    this.amendForm = this.fb.group({ reason: ['', [Validators.required, Validators.minLength(3)]] });
    this.amendDialogOpen.set(true);
  }

  closeAmendDialog(): void {
    this.amendDialogOpen.set(false);
    this.amendTarget.set(null);
  }

  submitAmend(): void {
    if (this.amendForm.invalid) return;
    const po = this.amendTarget();
    if (!po) return;
    this.savingAmend.set(true);
    this.http
      .post<PoRevisionDto>(`/api/procurement/purchase-orders/${po.id}/amend`, this.amendForm.value)
      .subscribe({
        next: (rev) => {
          this.snack.open(`Revision ${rev.revisionNo} recorded`, 'OK', { duration: 3000 });
          this.savingAmend.set(false);
          this.closeAmendDialog();
        },
        error: (e) => {
          this.snack.open(e.error?.detail ?? 'Error saving revision', 'Close', { duration: 4000 });
          this.savingAmend.set(false);
        },
      });
  }

  openRevisionsDialog(po: PurchaseOrderDto): void {
    this.amendTarget.set(po);
    this.revisionsLoading.set(true);
    this.revisionsDialogOpen.set(true);
    this.http.get<PoRevisionDto[]>(`/api/procurement/purchase-orders/${po.id}/revisions`).subscribe({
      next: (data) => {
        this.revisions.set(data);
        this.revisionsLoading.set(false);
      },
      error: () => {
        this.revisionsLoading.set(false);
        this.snack.open('Failed to load revisions', 'Close', { duration: 3000 });
      },
    });
  }

  closeRevisionsDialog(): void {
    this.revisionsDialogOpen.set(false);
    this.revisions.set([]);
    this.amendTarget.set(null);
  }
}
