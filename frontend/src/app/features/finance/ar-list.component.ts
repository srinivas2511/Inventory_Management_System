import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { LookupApi } from '../../shared/lookup.api';
import { FinanceApi, ArInvoiceDto, ArAgeingRow } from './finance.api';
import { SettlementDialogComponent } from './settlement-dialog.component';

@Component({
  selector: 'app-ar-list',
  standalone: true,
  imports: [
    DecimalPipe,
    HasPermissionDirective,
    MatButtonModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    MatTabsModule,
    RouterLink,
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
              <td mat-cell *matCellDef="let r">{{ customerName(r.customerId) }}</td>
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
            <ng-container matColumnDef="journal">
              <th mat-header-cell *matHeaderCellDef>Journal</th>
              <td mat-cell *matCellDef="let r">
                @if (r.journalHeaderId) {
                  <a [routerLink]="['/finance/journals', r.journalHeaderId]">View</a>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let r">
                @if (r.status === 'OPEN' || r.status === 'PARTIALLY_PAID') {
                  <button mat-stroked-button *appHasPermission="'AR_MANAGE'" (click)="recordReceipt(r)">
                    Record receipt
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
  private dialog = inject(MatDialog);
  private lookups = inject(LookupApi);
  private snack = inject(MatSnackBar);

  invoiceColumns = [
    'invoiceNumber',
    'customerId',
    'invoiceDate',
    'dueDate',
    'totalAmount',
    'balanceAmount',
    'status',
    'journal',
    'actions',
  ];
  ageingColumns = ['customerName', 'current', 'overdue30', 'overdue60', 'overdue90Plus', 'total'];
  invoices = signal<ArInvoiceDto[]>([]);
  ageing = signal<ArAgeingRow[]>([]);
  loading = signal(false);
  private customers = signal<Record<number, string>>({});

  ngOnInit() {
    this.lookups.customers().subscribe({
      next: (list) => this.customers.set(Object.fromEntries(list.map((c) => [c.id, `${c.code} · ${c.name}`]))),
    });
    this.load();
  }

  customerName(id: number): string {
    return this.customers()[id] ?? `#${id}`;
  }

  recordReceipt(invoice: ArInvoiceDto) {
    this.dialog
      .open(SettlementDialogComponent, {
        width: '520px',
        maxWidth: '96vw',
        data: {
          kind: 'AR',
          invoice: {
            id: invoice.id,
            invoiceNumber: invoice.invoiceNumber,
            balanceAmount: invoice.balanceAmount,
            counterparty: this.customerName(invoice.customerId),
          },
        },
      })
      .afterClosed()
      .subscribe((updated?: ArInvoiceDto) => {
        if (updated) {
          this.snack.open(
            `Receipt recorded on ${invoice.invoiceNumber}: ${updated.status.replace('_', ' ').toLowerCase()}, ` +
              `${updated.balanceAmount} outstanding`,
            'OK',
            { duration: 5000 },
          );
          this.load();
        }
      });
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
