import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { AlertDto, InsightApi } from './insight.api';

@Component({
  selector: 'app-alerts-list',
  standalone: true,
  providers: [InsightApi],
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    DatePipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Alerts</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="OPEN">Open</mat-option>
          <mat-option value="ACKNOWLEDGED">Acknowledged</mat-option>
          <mat-option value="RESOLVED">Resolved</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Severity</mat-label>
        <mat-select [formControl]="severityCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="CRITICAL">Critical</mat-option>
          <mat-option value="HIGH">High</mat-option>
          <mat-option value="MEDIUM">Medium</mat-option>
          <mat-option value="LOW">Low</mat-option>
        </mat-select>
      </mat-form-field>
    </div>

    @if (loading()) {
      <div class="spinner-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="alerts()" class="mat-elevation-z1">
        <ng-container matColumnDef="severity">
          <th mat-header-cell *matHeaderCellDef>Severity</th>
          <td mat-cell *matCellDef="let a"><app-status-badge [status]="a.severity" /></td>
        </ng-container>
        <ng-container matColumnDef="alertType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let a">{{ a.alertType }}</td>
        </ng-container>
        <ng-container matColumnDef="message">
          <th mat-header-cell *matHeaderCellDef>Message</th>
          <td mat-cell *matCellDef="let a">{{ a.message }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let a"><app-status-badge [status]="a.status" /></td>
        </ng-container>
        <ng-container matColumnDef="firstSeenAt">
          <th mat-header-cell *matHeaderCellDef>First Seen</th>
          <td mat-cell *matCellDef="let a">{{ a.firstSeenAt | date: 'short' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let a">
            @if (a.status === 'OPEN') {
              <button
                mat-icon-button
                matTooltip="Acknowledge"
                aria-label="Acknowledge"
                *appHasPermission="'ALERT_MANAGE'"
                (click)="acknowledge(a)"
              >
                <mat-icon>check</mat-icon>
              </button>
            }
            @if (a.status !== 'RESOLVED') {
              <button
                mat-icon-button
                matTooltip="Resolve"
                aria-label="Resolve"
                *appHasPermission="'ALERT_MANAGE'"
                (click)="resolve(a)"
              >
                <mat-icon>done_all</mat-icon>
              </button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let row; columns: cols"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell no-data" [attr.colspan]="cols.length">No alerts found</td>
        </tr>
      </table>
    }
  `,
  styles: `
    .filter-bar {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
      padding: 8px 0;
    }
    .spinner-center {
      display: flex;
      justify-content: center;
      padding: 48px;
    }
    .no-data {
      text-align: center;
      padding: 24px;
      color: rgba(0, 0, 0, 0.54);
    }
  `,
})
export class AlertsListComponent implements OnInit {
  private readonly api = inject(InsightApi);
  private readonly snack = inject(MatSnackBar);

  protected readonly cols = ['severity', 'alertType', 'message', 'status', 'firstSeenAt', 'actions'];
  protected readonly loading = signal(false);
  protected readonly alerts = signal<AlertDto[]>([]);

  protected readonly statusCtrl = new FormControl<string | null>(null);
  protected readonly severityCtrl = new FormControl<string | null>(null);

  ngOnInit(): void {
    this.load();
    this.statusCtrl.valueChanges.subscribe(() => this.load());
    this.severityCtrl.valueChanges.subscribe(() => this.load());
  }

  private load(): void {
    this.loading.set(true);
    this.api.searchAlerts(this.statusCtrl.value ?? undefined, this.severityCtrl.value ?? undefined).subscribe({
      next: (p) => {
        this.alerts.set(p.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected acknowledge(a: AlertDto): void {
    this.api.acknowledgeAlert(a.id).subscribe({
      next: () => {
        this.snack.open('Acknowledged', 'Dismiss', { duration: 3000 });
        this.load();
      },
      error: (e) => this.snack.open(e.error?.message ?? 'Error', 'Dismiss', { duration: 4000 }),
    });
  }

  protected resolve(a: AlertDto): void {
    this.api.resolveAlert(a.id).subscribe({
      next: () => {
        this.snack.open('Resolved', 'Dismiss', { duration: 3000 });
        this.load();
      },
      error: (e) => this.snack.open(e.error?.message ?? 'Error', 'Dismiss', { duration: 4000 }),
    });
  }
}
