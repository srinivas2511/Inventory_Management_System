import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { FinanceApi, BankStatementDto } from './finance.api';

@Component({
  selector: 'app-bank-reconciliation',
  standalone: true,
  imports: [DecimalPipe, MatButtonModule, MatProgressSpinnerModule, MatTableModule, StatusBadgeComponent],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Bank Reconciliation</h2>
      <button mat-stroked-button (click)="load()">Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="statements()" class="mat-elevation-z1">
        <ng-container matColumnDef="statementNumber">
          <th mat-header-cell *matHeaderCellDef>Statement #</th>
          <td mat-cell *matCellDef="let r">{{ r.statementNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="statementDate">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let r">{{ r.statementDate }}</td>
        </ng-container>
        <ng-container matColumnDef="closingBalance">
          <th mat-header-cell *matHeaderCellDef>Closing</th>
          <td mat-cell *matCellDef="let r">{{ r.closingBalance | number: '1.2-4' }}</td>
        </ng-container>
        <ng-container matColumnDef="matched">
          <th mat-header-cell *matHeaderCellDef>Matched / Total</th>
          <td mat-cell *matCellDef="let r">{{ r.matchedLines }} / {{ r.totalLines }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            @if (r.status === 'OPEN') {
              <button mat-stroked-button (click)="autoMatch(r)">Auto-Match</button>
              <button mat-flat-button color="primary" (click)="reconcile(r)" style="margin-left:8px">Reconcile</button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class BankReconciliationComponent implements OnInit {
  private api = inject(FinanceApi);

  columns = ['statementNumber', 'statementDate', 'closingBalance', 'matched', 'status', 'actions'];
  statements = signal<BankStatementDto[]>([]);
  loading = signal(false);

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.api.getBankStatements().subscribe({
      next: (p) => {
        this.statements.set(p.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  autoMatch(stmt: BankStatementDto) {
    this.api.autoMatchStatement(stmt.id).subscribe(() => this.load());
  }

  reconcile(stmt: BankStatementDto) {
    this.api.reconcileStatement(stmt.id).subscribe(() => this.load());
  }
}
