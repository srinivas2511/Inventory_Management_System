import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { FinanceApi, ApInvoiceDto, ApAgeingRow } from './finance.api';

@Component({
  selector: 'app-ap-list',
  standalone: true,
  imports: [
    DecimalPipe,
    FormsModule,
    HasPermissionDirective,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    MatTabsModule,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Accounts Payable</h2>
      <button mat-stroked-button (click)="load()">Refresh</button>
    </div>

    <mat-tab-group>
      <mat-tab label="Invoices">
        @if (loading()) {
          <div class="loading-center"><mat-spinner diameter="40" /></div>
        } @else {
          @if (overrideTarget(); as target) {
            <section class="override-panel" aria-label="Approve match variance">
              <h3 class="mat-h3">Approve variance on {{ target.invoiceNumber }}</h3>
              <p>{{ target.matchMessage }}</p>
              <mat-form-field appearance="outline" class="reason-field">
                <mat-label>Reason for approving</mat-label>
                <textarea matInput rows="2" maxlength="500" [(ngModel)]="reason"></textarea>
              </mat-form-field>
              <div class="override-actions">
                <button
                  mat-flat-button
                  color="primary"
                  [disabled]="!reason.trim() || saving()"
                  (click)="approveVariance(target)"
                >
                  Approve variance
                </button>
                <button mat-button [disabled]="saving()" (click)="closeOverride()">Cancel</button>
              </div>
            </section>
          }
          <table mat-table [dataSource]="invoices()" class="mat-elevation-z1">
            <ng-container matColumnDef="invoiceNumber">
              <th mat-header-cell *matHeaderCellDef>Invoice #</th>
              <td mat-cell *matCellDef="let r">{{ r.invoiceNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="supplierId">
              <th mat-header-cell *matHeaderCellDef>Supplier</th>
              <td mat-cell *matCellDef="let r">{{ r.supplierId }}</td>
            </ng-container>
            <ng-container matColumnDef="invoiceDate">
              <th mat-header-cell *matHeaderCellDef>Date</th>
              <td mat-cell *matCellDef="let r">{{ r.invoiceDate }}</td>
            </ng-container>
            <ng-container matColumnDef="totalAmount">
              <th mat-header-cell *matHeaderCellDef>Total</th>
              <td mat-cell *matCellDef="let r">{{ r.totalAmount | number: '1.2-4' }}</td>
            </ng-container>
            <ng-container matColumnDef="balanceAmount">
              <th mat-header-cell *matHeaderCellDef>Balance</th>
              <td mat-cell *matCellDef="let r">{{ r.balanceAmount | number: '1.2-4' }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
            </ng-container>
            <ng-container matColumnDef="match">
              <th mat-header-cell *matHeaderCellDef>Match</th>
              <td mat-cell *matCellDef="let r">
                <app-status-badge [status]="r.matchStatus" />
                @if (r.matchStatus === 'EXCEPTION' && r.matchMessage) {
                  <div class="match-message">{{ r.matchMessage }}</div>
                }
                @if (r.matchStatus === 'OVERRIDDEN' && r.matchVarianceAmount > 0) {
                  <div class="match-message">Price variance {{ r.matchVarianceAmount | number: '1.2-4' }}</div>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let r">
                @if (r.matchStatus === 'EXCEPTION') {
                  <button mat-stroked-button *appHasPermission="'AP_MATCH_OVERRIDE'" (click)="openOverride(r)">
                    Approve variance
                  </button>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="invoiceColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: invoiceColumns"></tr>
          </table>
        }
      </mat-tab>
      <mat-tab label="Ageing">
        <table mat-table [dataSource]="ageing()" class="mat-elevation-z1">
          <ng-container matColumnDef="supplierName">
            <th mat-header-cell *matHeaderCellDef>Supplier</th>
            <td mat-cell *matCellDef="let r">{{ r.supplierName }}</td>
          </ng-container>
          <ng-container matColumnDef="current">
            <th mat-header-cell *matHeaderCellDef>Current</th>
            <td mat-cell *matCellDef="let r">{{ r.current | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="overdue30">
            <th mat-header-cell *matHeaderCellDef>1–30d</th>
            <td mat-cell *matCellDef="let r">{{ r.overdue30 | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="overdue60">
            <th mat-header-cell *matHeaderCellDef>31–60d</th>
            <td mat-cell *matCellDef="let r">{{ r.overdue60 | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="overdue90Plus">
            <th mat-header-cell *matHeaderCellDef>>90d</th>
            <td mat-cell *matCellDef="let r">{{ r.overdue90Plus | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="total">
            <th mat-header-cell *matHeaderCellDef>Total</th>
            <td mat-cell *matCellDef="let r">{{ r.total | number: '1.2-2' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="ageingColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: ageingColumns"></tr>
        </table>
      </mat-tab>
    </mat-tab-group>
  `,
  styles: [
    `
      .override-panel {
        padding: 16px;
        margin-bottom: 16px;
        border: 1px solid var(--mat-sys-outline-variant, #c4c7c5);
        border-radius: 4px;
      }
      .reason-field {
        width: 100%;
        max-width: 560px;
      }
      .override-actions {
        display: flex;
        gap: 8px;
      }
      .match-message {
        max-width: 360px;
        font-size: 12px;
        opacity: 0.8;
      }
    `,
  ],
})
export class ApListComponent implements OnInit {
  private api = inject(FinanceApi);
  private snack = inject(MatSnackBar);

  invoiceColumns = [
    'invoiceNumber',
    'supplierId',
    'invoiceDate',
    'totalAmount',
    'balanceAmount',
    'status',
    'match',
    'actions',
  ];
  ageingColumns = ['supplierName', 'current', 'overdue30', 'overdue60', 'overdue90Plus', 'total'];
  invoices = signal<ApInvoiceDto[]>([]);
  ageing = signal<ApAgeingRow[]>([]);
  loading = signal(false);
  overrideTarget = signal<ApInvoiceDto | null>(null);
  saving = signal(false);
  reason = '';

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.api.getApInvoices().subscribe({
      next: (p) => {
        this.invoices.set(p.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.api.getApAgeing().subscribe({ next: (a) => this.ageing.set(a) });
  }

  openOverride(invoice: ApInvoiceDto) {
    this.reason = '';
    this.overrideTarget.set(invoice);
  }

  closeOverride() {
    this.overrideTarget.set(null);
    this.reason = '';
  }

  approveVariance(invoice: ApInvoiceDto) {
    this.saving.set(true);
    this.api.overrideApMatch(invoice.id, this.reason.trim()).subscribe({
      next: () => {
        this.saving.set(false);
        this.closeOverride();
        this.snack.open(`Variance approved on ${invoice.invoiceNumber}`, 'OK', { duration: 4000 });
        this.load();
      },
      error: (e) => {
        this.saving.set(false);
        this.snack.open(e.error?.detail ?? 'Could not approve the variance', 'OK', { duration: 6000 });
      },
    });
  }
}
