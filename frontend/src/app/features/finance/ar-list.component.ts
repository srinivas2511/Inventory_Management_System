import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { FinanceApi, ArInvoiceDto, ArAgeingRow } from './finance.api';

@Component({
  selector: 'app-ar-list',
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
      <h2 class="mat-h2">Accounts Receivable</h2>
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
            <ng-container matColumnDef="customerId">
              <th mat-header-cell *matHeaderCellDef>Customer</th>
              <td mat-cell *matCellDef="let r">{{ r.customerId }}</td>
            </ng-container>
            <ng-container matColumnDef="invoiceDate">
              <th mat-header-cell *matHeaderCellDef>Date</th>
              <td mat-cell *matCellDef="let r">{{ r.invoiceDate }}</td>
            </ng-container>
            <ng-container matColumnDef="dueDate">
              <th mat-header-cell *matHeaderCellDef>Due</th>
              <td mat-cell *matCellDef="let r">{{ r.dueDate }}</td>
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
          <ng-container matColumnDef="customerName">
            <th mat-header-cell *matHeaderCellDef>Customer</th>
            <td mat-cell *matCellDef="let r">{{ r.customerName }}</td>
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
export class ArListComponent implements OnInit {
  private api = inject(FinanceApi);

  invoiceColumns = ['invoiceNumber', 'customerId', 'invoiceDate', 'dueDate', 'totalAmount', 'balanceAmount', 'status'];
  ageingColumns = ['customerName', 'current', 'overdue30', 'overdue60', 'overdue90Plus', 'total'];
  invoices = signal<ArInvoiceDto[]>([]);
  ageing = signal<ArAgeingRow[]>([]);
  loading = signal(false);

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.api.getArInvoices().subscribe({
      next: (p) => {
        this.invoices.set(p.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.api.getArAgeing().subscribe({ next: (a) => this.ageing.set(a) });
  }
}
