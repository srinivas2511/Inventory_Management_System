import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';

export interface IntegrationCallDto {
  id: number;
  connector: string;
  direction: string;
  requestSummary: string;
  statusCode: number;
  ok: boolean;
  durationMs: number;
  correlationId: string;
  errorMessage: string;
  createdAt: string;
}

@Component({
  selector: 'app-integration-call-log',
  standalone: true,
  imports: [
    DecimalPipe,
    DatePipe,
    IstDatePipe,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatTableModule,
    MatSnackBarModule,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.integration_call_log">Integration Call Log</h2>
      <button mat-icon-button (click)="load()" aria-label="Refresh">
        <mat-icon aria-hidden="true">refresh</mat-icon>
      </button>
    </div>
    <mat-card>
      <mat-card-content>
        <table mat-table [dataSource]="calls()" class="full-width">
          <ng-container matColumnDef="connector">
            <th mat-header-cell *matHeaderCellDef>Connector</th>
            <td mat-cell *matCellDef="let row">{{ row.connector }}</td>
          </ng-container>
          <ng-container matColumnDef="direction">
            <th mat-header-cell *matHeaderCellDef>Dir</th>
            <td mat-cell *matCellDef="let row">{{ row.direction }}</td>
          </ng-container>
          <ng-container matColumnDef="requestSummary">
            <th mat-header-cell *matHeaderCellDef>Request</th>
            <td mat-cell *matCellDef="let row">{{ row.requestSummary }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let row">
              <mat-chip [color]="row.ok ? 'primary' : 'warn'" highlighted>{{ row.statusCode }}</mat-chip>
            </td>
          </ng-container>
          <ng-container matColumnDef="durationMs">
            <th mat-header-cell *matHeaderCellDef>ms</th>
            <td mat-cell *matCellDef="let row">{{ row.durationMs }}</td>
          </ng-container>
          <ng-container matColumnDef="createdAt">
            <th mat-header-cell *matHeaderCellDef>Time</th>
            <td mat-cell *matCellDef="let row">{{ row.createdAt | istDate: 'dd/MM/yy HH:mm' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="cols"></tr>
          <tr mat-row *matRowDef="let row; columns: cols"></tr>
        </table>
        @if (calls().length === 0) {
          <p class="empty-state">No calls logged yet.</p>
        }
      </mat-card-content>
    </mat-card>
  `,
})
export class IntegrationCallLogComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  calls = signal<IntegrationCallDto[]>([]);
  cols = ['connector', 'direction', 'requestSummary', 'status', 'durationMs', 'createdAt'];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.http.get<IntegrationCallDto[]>('/api/integration/calls').subscribe({
      next: (data) => this.calls.set(data),
      error: () => this.snackBar.open('Failed to load call log', 'Close', { duration: 3000 }),
    });
  }
}
