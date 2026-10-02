import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { FinanceApi, ApInvoiceDto, ApAgeingRow } from './finance.api';

@Component({
  selector: 'app-ap-list',
  standalone: true,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatProgressSpinnerModule,
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
})
export class ApListComponent implements OnInit {
  private api = inject(FinanceApi);

  invoiceColumns = ['invoiceNumber', 'supplierId', 'invoiceDate', 'totalAmount', 'balanceAmount', 'status'];
  ageingColumns = ['supplierName', 'current', 'overdue30', 'overdue60', 'overdue90Plus', 'total'];
  invoices = signal<ApInvoiceDto[]>([]);
  ageing = signal<ApAgeingRow[]>([]);
  loading = signal(false);

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
}
