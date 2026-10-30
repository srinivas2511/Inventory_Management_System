import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';

export interface JwoDto {
  id: number;
  docNo: string;
  docDate: string | null;
  status: string;
  vendorId: number;
  challanNo: string | null;
  issueDate: string | null;
  expectedReturn: string | null;
  actualReturn: string | null;
  jobCharges: number | null;
  scrapQty: number | null;
  remarks: string | null;
  items: JwoItemDto[];
}

export interface JwoItemDto {
  id: number;
  lineNo: number;
  materialId: number | null;
  productId: number | null;
  description: string | null;
  sentQty: number;
  receivedQty: number;
  scrappedQty: number;
  uom: string | null;
}

@Component({
  selector: 'app-job-work-order-list',
  standalone: true,
  imports: [
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatSnackBarModule,
    IstDatePipe,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.job_work_orders">Job Work Orders</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="orders()" class="mat-elevation-z2 full-width">
        <ng-container matColumnDef="docNo">
          <th mat-header-cell *matHeaderCellDef>Doc No</th>
          <td mat-cell *matCellDef="let row">{{ row.docNo }}</td>
        </ng-container>
        <ng-container matColumnDef="docDate">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let row">{{ row.docDate | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let row"><app-status-badge [status]="row.status" /></td>
        </ng-container>
        <ng-container matColumnDef="challanNo">
          <th mat-header-cell *matHeaderCellDef>Challan No</th>
          <td mat-cell *matCellDef="let row">{{ row.challanNo }}</td>
        </ng-container>
        <ng-container matColumnDef="issueDate">
          <th mat-header-cell *matHeaderCellDef>Issue Date</th>
          <td mat-cell *matCellDef="let row">{{ row.issueDate | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="expectedReturn">
          <th mat-header-cell *matHeaderCellDef>Expected Return</th>
          <td mat-cell *matCellDef="let row">{{ row.expectedReturn | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="jobCharges">
          <th mat-header-cell *matHeaderCellDef>Job Charges</th>
          <td mat-cell *matCellDef="let row">{{ row.jobCharges | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="items">
          <th mat-header-cell *matHeaderCellDef>Lines</th>
          <td mat-cell *matCellDef="let row">{{ row.items?.length ?? 0 }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let row">
            @if (row.status === 'DRAFT' || row.status === 'CONFIRMED') {
              <button
                mat-icon-button
                aria-label="Issue materials"
                title="Issue Materials"
                (click)="openIssueDialog(row)"
              >
                <mat-icon aria-hidden="true">send</mat-icon>
              </button>
              <button
                mat-icon-button
                aria-label="Record charges"
                title="Record Charges"
                (click)="openChargesDialog(row)"
              >
                <mat-icon aria-hidden="true">receipt</mat-icon>
              </button>
            }
            @if (row.status === 'ISSUED' || row.status === 'PARTIALLY_RECEIVED') {
              <button mat-icon-button aria-label="Receive job work" title="Receive" (click)="openReceiveDialog(row)">
                <mat-icon aria-hidden="true">move_to_inbox</mat-icon>
              </button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }

    <!-- Issue dialog -->
    @if (issueDialogOpen()) {
      <div class="dialog-backdrop" (click)="closeIssueDialog()" (keydown.escape)="closeIssueDialog()" tabindex="-1">
        <div
          class="dialog-panel"
          (click)="$event.stopPropagation()"
          (keydown)="$event.stopPropagation()"
          role="dialog"
          aria-modal="true"
          aria-label="Issue Materials"
          tabindex="-1"
        >
          <h3>Issue Materials — {{ activeOrder()?.docNo }}</h3>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Material ID</mat-label>
            <input matInput type="number" [(ngModel)]="issueForm.materialId" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Quantity</mat-label>
            <input matInput type="number" [(ngModel)]="issueForm.qty" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Location ID</mat-label>
            <input matInput type="number" [(ngModel)]="issueForm.locationId" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Notes</mat-label>
            <input matInput [(ngModel)]="issueForm.notes" />
          </mat-form-field>
          <div class="dialog-actions">
            <button mat-button (click)="closeIssueDialog()">Cancel</button>
            <button mat-flat-button color="primary" (click)="submitIssue()">Issue</button>
          </div>
        </div>
      </div>
    }

    <!-- Receive dialog -->
    @if (receiveDialogOpen()) {
      <div class="dialog-backdrop" (click)="closeReceiveDialog()" (keydown.escape)="closeReceiveDialog()" tabindex="-1">
        <div
          class="dialog-panel"
          (click)="$event.stopPropagation()"
          (keydown)="$event.stopPropagation()"
          role="dialog"
          aria-modal="true"
          aria-label="Receive Job Work"
          tabindex="-1"
        >
          <h3>Receive — {{ activeOrder()?.docNo }}</h3>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Received Qty</mat-label>
            <input matInput type="number" [(ngModel)]="receiveForm.receivedQty" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Scrap Qty</mat-label>
            <input matInput type="number" [(ngModel)]="receiveForm.scrapQty" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Notes</mat-label>
            <input matInput [(ngModel)]="receiveForm.notes" />
          </mat-form-field>
          <div class="dialog-actions">
            <button mat-button (click)="closeReceiveDialog()">Cancel</button>
            <button mat-flat-button color="primary" (click)="submitReceive()">Receive</button>
          </div>
        </div>
      </div>
    }

    <!-- Charges dialog -->
    @if (chargesDialogOpen()) {
      <div class="dialog-backdrop" (click)="closeChargesDialog()" (keydown.escape)="closeChargesDialog()" tabindex="-1">
        <div
          class="dialog-panel"
          (click)="$event.stopPropagation()"
          (keydown)="$event.stopPropagation()"
          role="dialog"
          aria-modal="true"
          aria-label="Record Charges"
          tabindex="-1"
        >
          <h3>Record Charges — {{ activeOrder()?.docNo }}</h3>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Description</mat-label>
            <input matInput [(ngModel)]="chargesForm.chargeDescription" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Amount</mat-label>
            <input matInput type="number" [(ngModel)]="chargesForm.amount" />
          </mat-form-field>
          <div class="dialog-actions">
            <button mat-button (click)="closeChargesDialog()">Cancel</button>
            <button mat-flat-button color="primary" (click)="submitCharges()">Save</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .dialog-backdrop {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(0, 0, 0, 0.4);
        z-index: 1000;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .dialog-panel {
        background: #fff;
        border-radius: 8px;
        padding: 24px;
        min-width: 320px;
        max-width: 480px;
        width: 100%;
      }
      .dialog-actions {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
        margin-top: 16px;
      }
    `,
  ],
})
export class JobWorkOrderListComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  columns = [
    'docNo',
    'docDate',
    'status',
    'challanNo',
    'issueDate',
    'expectedReturn',
    'jobCharges',
    'items',
    'actions',
  ];
  orders = signal<JwoDto[]>([]);
  loading = signal(true);
  activeOrder = signal<JwoDto | null>(null);

  issueDialogOpen = signal(false);
  receiveDialogOpen = signal(false);
  chargesDialogOpen = signal(false);

  issueForm = {
    materialId: null as number | null,
    qty: null as number | null,
    locationId: null as number | null,
    notes: '',
  };
  receiveForm = { receivedQty: null as number | null, scrapQty: 0, notes: '' };
  chargesForm = { chargeDescription: '', amount: null as number | null };

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
    this.http.get<JwoDto[]>('/api/production/job-work-orders').subscribe({
      next: (data) => {
        this.orders.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load job work orders', 'Close', { duration: 3000 });
      },
    });
  }

  openIssueDialog(order: JwoDto): void {
    this.activeOrder.set(order);
    this.issueForm = { materialId: null, qty: null, locationId: null, notes: '' };
    this.issueDialogOpen.set(true);
  }
  closeIssueDialog(): void {
    this.issueDialogOpen.set(false);
  }

  openReceiveDialog(order: JwoDto): void {
    this.activeOrder.set(order);
    this.receiveForm = { receivedQty: null, scrapQty: 0, notes: '' };
    this.receiveDialogOpen.set(true);
  }
  closeReceiveDialog(): void {
    this.receiveDialogOpen.set(false);
  }

  openChargesDialog(order: JwoDto): void {
    this.activeOrder.set(order);
    this.chargesForm = { chargeDescription: '', amount: null };
    this.chargesDialogOpen.set(true);
  }
  closeChargesDialog(): void {
    this.chargesDialogOpen.set(false);
  }

  submitIssue(): void {
    const order = this.activeOrder();
    if (!order) return;
    const body = {
      lines: [
        {
          materialId: this.issueForm.materialId,
          qty: this.issueForm.qty,
          locationId: this.issueForm.locationId,
          notes: this.issueForm.notes,
        },
      ],
    };
    this.http.post<JwoDto>(`/api/production/job-work-orders/${order.id}/issue-materials`, body).subscribe({
      next: () => {
        this.snackBar.open('Materials issued', 'Close', { duration: 3000 });
        this.closeIssueDialog();
        this.loadOrders();
      },
      error: () => this.snackBar.open('Failed to issue materials', 'Close', { duration: 3000 }),
    });
  }

  submitReceive(): void {
    const order = this.activeOrder();
    if (!order) return;
    const body = {
      receivedQty: this.receiveForm.receivedQty,
      scrapQty: this.receiveForm.scrapQty,
      notes: this.receiveForm.notes,
    };
    this.http.post<JwoDto>(`/api/production/job-work-orders/${order.id}/receive`, body).subscribe({
      next: () => {
        this.snackBar.open('Goods received', 'Close', { duration: 3000 });
        this.closeReceiveDialog();
        this.loadOrders();
      },
      error: () => this.snackBar.open('Failed to receive', 'Close', { duration: 3000 }),
    });
  }

  submitCharges(): void {
    const order = this.activeOrder();
    if (!order) return;
    const body = {
      chargeDescription: this.chargesForm.chargeDescription,
      amount: this.chargesForm.amount,
      chargeDate: null,
    };
    this.http.post(`/api/production/job-work-orders/${order.id}/record-charges`, body).subscribe({
      next: () => {
        this.snackBar.open('Charges recorded', 'Close', { duration: 3000 });
        this.closeChargesDialog();
      },
      error: () => this.snackBar.open('Failed to record charges', 'Close', { duration: 3000 }),
    });
  }
}
