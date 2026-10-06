import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ArReconcileResultDto, FinanceAlertDto, FinanceApi, MissingArInvoiceDto } from './finance.api';

@Component({
  selector: 'app-ar-reconciliation',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">AR Invoice Reconciliation</h2>
      <div>
        <button mat-stroked-button (click)="load()" [disabled]="loading() || retrying()">
          <mat-icon>refresh</mat-icon> Refresh
        </button>
        <button
          mat-raised-button
          color="primary"
          *appHasPermission="'AR_RECONCILE'"
          [disabled]="rows().length === 0 || retrying()"
          (click)="retry()"
        >
          <mat-icon>replay</mat-icon> Retry all
        </button>
      </div>
    </div>

    <p class="hint">
      Dispatches that left the warehouse without an AR invoice. Retrying creates the missing invoices and their revenue
      journals; it needs the AR reconcile permission. A dispatch dated in a missing or closed fiscal period keeps
      failing until a period is open for it.
    </p>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else if (rows().length === 0) {
      <p class="empty">Every dispatch has an AR invoice.</p>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="dispatchNumber">
          <th mat-header-cell *matHeaderCellDef>Dispatch</th>
          <td mat-cell *matCellDef="let r">{{ r.dispatchNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="dispatchDate">
          <th mat-header-cell *matHeaderCellDef>Dispatch date</th>
          <td mat-cell *matCellDef="let r">{{ r.dispatchDate | date: 'dd/MM/yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="customerId">
          <th mat-header-cell *matHeaderCellDef>Customer</th>
          <td mat-cell *matCellDef="let r">{{ r.customerId }}</td>
        </ng-container>
        <ng-container matColumnDef="alert">
          <th mat-header-cell *matHeaderCellDef>Alert</th>
          <td mat-cell *matCellDef="let r">
            @if (alertFor(r.dispatchId); as a) {
              <span class="alert-open" [title]="a.message">
                {{ a.severity }} · seen {{ a.lastSeenAt | date: 'dd/MM/yyyy HH:mm' }}
              </span>
              <div class="alert-msg">{{ a.message }}</div>
            } @else {
              <span class="muted">No open alert</span>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="failure">
          <th mat-header-cell *matHeaderCellDef>Last retry</th>
          <td mat-cell *matCellDef="let r">{{ failureFor(r.dispatchId) ?? '' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let r; columns: columns"></tr>
      </table>
    }

    @if (result(); as res) {
      <div class="result">
        <h3 class="mat-h3">Last retry</h3>
        <p>
          Attempted {{ res.attempted }} · created {{ res.created }} · already invoiced {{ res.alreadyInvoiced }} ·
          failed {{ res.failed }}
        </p>
        @if (res.failures.length > 0) {
          <ul>
            @for (f of res.failures; track f.dispatchId) {
              <li>
                <strong>{{ f.dispatchNumber }}</strong
                >: {{ f.reason }}
              </li>
            }
          </ul>
        }
      </div>
    }
  `,
  styles: [
    `
      .hint {
        max-width: 900px;
        color: #555;
      }
      .muted {
        color: #888;
      }
      .alert-open {
        color: #b71c1c;
        font-weight: 500;
      }
      .alert-msg {
        font-size: 12px;
        color: #555;
        max-width: 420px;
      }
      .result {
        margin-top: 16px;
      }
      .empty {
        padding: 16px 0;
      }
    `,
  ],
})
export class ArReconciliationComponent implements OnInit {
  private api = inject(FinanceApi);
  private snack = inject(MatSnackBar);

  columns = ['dispatchNumber', 'dispatchDate', 'customerId', 'alert', 'failure'];
  rows = signal<MissingArInvoiceDto[]>([]);
  alerts = signal<FinanceAlertDto[]>([]);
  result = signal<ArReconcileResultDto | null>(null);
  loading = signal(false);
  retrying = signal(false);

  private openAlerts = computed(() => {
    const byDispatch = new Map<number, FinanceAlertDto>();
    for (const a of this.alerts()) {
      if (a.entityType === 'DISPATCH' && !a.resolvedAt) {
        byDispatch.set(a.entityId, a);
      }
    }
    return byDispatch;
  });

  private failures = computed(() => {
    const byDispatch = new Map<number, string>();
    for (const f of this.result()?.failures ?? []) {
      byDispatch.set(f.dispatchId, f.reason);
    }
    return byDispatch;
  });

  ngOnInit(): void {
    this.load();
  }

  alertFor(dispatchId: number): FinanceAlertDto | undefined {
    return this.openAlerts().get(dispatchId);
  }

  failureFor(dispatchId: number): string | undefined {
    return this.failures().get(dispatchId);
  }

  load(): void {
    this.loading.set(true);
    this.api.getMissingArInvoices().subscribe({
      next: (rows) => {
        this.rows.set(rows ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.rows.set([]);
        this.loading.set(false);
      },
    });
    // Alerts need ALERT_VIEW, which an AR-only user may lack; the table still works without them.
    this.api.getMissingArInvoiceAlerts().subscribe({
      next: (page) => this.alerts.set(page.content ?? []),
      error: () => this.alerts.set([]),
    });
  }

  retry(): void {
    this.retrying.set(true);
    this.api.retryMissingArInvoices().subscribe({
      next: (res) => {
        this.result.set(res);
        this.retrying.set(false);
        this.snack.open(`Created ${res.created} invoice(s), ${res.failed} failed`, 'OK', { duration: 4000 });
        this.load();
      },
      error: (e) => {
        this.retrying.set(false);
        this.snack.open(e.error?.detail ?? 'Retry failed', 'OK', { duration: 4000 });
      },
    });
  }
}
