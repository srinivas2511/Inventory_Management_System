import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { FinanceApi, FiscalPeriodDto } from './finance.api';

@Component({
  selector: 'app-period-list',
  standalone: true,
  imports: [MatButtonModule, MatProgressSpinnerModule, MatTableModule, StatusBadgeComponent],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Fiscal Periods</h2>
      <button mat-stroked-button (click)="load()">Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="periods()" class="mat-elevation-z1">
        <ng-container matColumnDef="periodName">
          <th mat-header-cell *matHeaderCellDef>Period</th>
          <td mat-cell *matCellDef="let r">{{ r.periodName }}</td>
        </ng-container>
        <ng-container matColumnDef="fiscalYear">
          <th mat-header-cell *matHeaderCellDef>Year</th>
          <td mat-cell *matCellDef="let r">{{ r.fiscalYear }}</td>
        </ng-container>
        <ng-container matColumnDef="startDate">
          <th mat-header-cell *matHeaderCellDef>Start</th>
          <td mat-cell *matCellDef="let r">{{ r.startDate }}</td>
        </ng-container>
        <ng-container matColumnDef="endDate">
          <th mat-header-cell *matHeaderCellDef>End</th>
          <td mat-cell *matCellDef="let r">{{ r.endDate }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            @if (r.status === 'OPEN') {
              <button mat-stroked-button (click)="initiateClose(r)">Initiate Close</button>
            }
            @if (r.status === 'CLOSING') {
              <button mat-flat-button color="warn" (click)="finalizeClose(r)">Finalize</button>
            }
            @if (r.status === 'CLOSED') {
              <button mat-stroked-button (click)="reopen(r)">Reopen</button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class PeriodListComponent implements OnInit {
  private api = inject(FinanceApi);

  columns = ['periodName', 'fiscalYear', 'startDate', 'endDate', 'status', 'actions'];
  periods = signal<FiscalPeriodDto[]>([]);
  loading = signal(false);

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.api.getPeriods().subscribe({
      next: (p) => {
        this.periods.set(p);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  initiateClose(p: FiscalPeriodDto) {
    this.api.initiatePeriodClose(p.id).subscribe(() => this.load());
  }

  finalizeClose(p: FiscalPeriodDto) {
    this.api.finalizePeriodClose(p.id).subscribe(() => this.load());
  }

  reopen(p: FiscalPeriodDto) {
    this.api.reopenPeriod(p.id).subscribe(() => this.load());
  }
}
