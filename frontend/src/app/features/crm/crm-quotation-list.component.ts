import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { CrmApi, QuotationDto } from './crm.api';

const STATUSES = ['DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CONVERTED'];

@Component({
  selector: 'app-crm-quotation-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Quotations</h2>
      <a *appHasPermission="'QUOTATION_CREATE'" mat-flat-button color="primary" routerLink="/crm/quotations/new">
        <mat-icon>add</mat-icon> New quotation
      </a>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl" (selectionChange)="load()">
          <mat-option [value]="null">All</mat-option>
          @for (s of statuses; track s) {
            <mat-option [value]="s">{{ s }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </div>

    @if (rejecting(); as q) {
      <div class="reject-box">
        <mat-form-field appearance="outline" class="wide">
          <mat-label>Why did the customer reject {{ q.quotationNumber }}?</mat-label>
          <input matInput maxlength="300" [formControl]="reasonCtrl" />
        </mat-form-field>
        <button mat-flat-button color="warn" [disabled]="!reasonCtrl.value.trim()" (click)="confirmReject()">
          Reject
        </button>
        <button mat-button (click)="rejecting.set(null)">Cancel</button>
      </div>
    }

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="quotations()" class="mat-elevation-z1">
        <ng-container matColumnDef="number">
          <th mat-header-cell *matHeaderCellDef>Quotation</th>
          <td mat-cell *matCellDef="let r">{{ r.quotationNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="customer">
          <th mat-header-cell *matHeaderCellDef>Customer</th>
          <td mat-cell *matCellDef="let r">{{ r.customerName }}</td>
        </ng-container>
        <ng-container matColumnDef="validUntil">
          <th mat-header-cell *matHeaderCellDef>Valid until</th>
          <td mat-cell *matCellDef="let r">{{ r.validUntil | date: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="total">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let r">{{ r.totalAmount | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="order">
          <th mat-header-cell *matHeaderCellDef>Sales order</th>
          <td mat-cell *matCellDef="let r">{{ r.convertedSalesOrderNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <span *appHasPermission="'QUOTATION_CREATE'">
              @if (r.allowedActions.includes('EDIT')) {
                <button mat-button (click)="edit(r)">Edit</button>
              }
              @if (r.allowedActions.includes('SEND')) {
                <button mat-button color="primary" (click)="run(r, 'send')">Send</button>
              }
              @if (r.allowedActions.includes('ACCEPT')) {
                <button mat-button color="primary" (click)="run(r, 'accept')">Accept</button>
              }
              @if (r.allowedActions.includes('REJECT')) {
                <button mat-button color="warn" (click)="startReject(r)">Reject</button>
              }
              @if (r.allowedActions.includes('CONVERT')) {
                <button mat-flat-button color="primary" (click)="run(r, 'convert')">Convert to sales order</button>
              }
            </span>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No quotations found
          </td>
        </tr>
      </table>
    }
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: center;
      gap: 16px;
    }
    .filter-bar,
    .reject-box {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .wide {
      min-width: 320px;
    }
  `,
})
export class CrmQuotationListComponent implements OnInit {
  private api = inject(CrmApi);
  private snack = inject(MatSnackBar);
  private router = inject(Router);

  statuses = STATUSES;
  columns = ['number', 'customer', 'validUntil', 'total', 'status', 'order', 'actions'];
  quotations = signal<QuotationDto[]>([]);
  loading = signal(false);
  rejecting = signal<QuotationDto | null>(null);

  statusCtrl = new FormControl<string | null>(null);
  reasonCtrl = new FormControl('', { nonNullable: true });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getQuotations(this.statusCtrl.value).subscribe({
      next: (page) => {
        this.quotations.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load quotations', 'Dismiss', { duration: 3000 });
      },
    });
  }

  edit(q: QuotationDto): void {
    this.router.navigate(['/crm/quotations', q.id, 'edit']);
  }

  run(q: QuotationDto, action: 'send' | 'accept' | 'convert'): void {
    const call =
      action === 'send'
        ? this.api.sendQuotation(q.id)
        : action === 'accept'
          ? this.api.acceptQuotation(q.id)
          : this.api.convertQuotation(q.id);
    call.subscribe({
      next: (result) => {
        if (action === 'convert') {
          this.snack.open(`Sales order ${result.convertedSalesOrderNumber} created`, 'OK', { duration: 5000 });
        }
        this.load();
      },
      error: (err) => this.snack.open(err?.error?.detail ?? 'Action failed', 'Dismiss', { duration: 5000 }),
    });
  }

  startReject(q: QuotationDto): void {
    this.reasonCtrl.reset('');
    this.rejecting.set(q);
  }

  confirmReject(): void {
    const q = this.rejecting();
    if (!q) return;
    this.api.rejectQuotation(q.id, this.reasonCtrl.value.trim()).subscribe({
      next: () => {
        this.rejecting.set(null);
        this.load();
      },
      error: (err) => this.snack.open(err?.error?.detail ?? 'Action failed', 'Dismiss', { duration: 5000 }),
    });
  }
}
