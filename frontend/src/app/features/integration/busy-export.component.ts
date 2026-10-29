import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';

interface QueueRow {
  id: number;
  journalId: number;
  journalNumber: string;
  status: string;
  exportAttempt: number;
  lastError: string | null;
  exportedAt: string | null;
  createdAt: string;
}

interface ExportResult {
  queued: number;
  exported: number;
  failed: number;
}

@Component({
  selector: 'app-busy-export',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatSnackBarModule,
    IstDatePipe,
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <h2 i18n="@@nav.item-busy-export">Busy Accounting Export</h2>
        <div class="actions">
          <button mat-stroked-button color="primary" (click)="stage()">Stage pending journals</button>
          <button mat-flat-button color="primary" (click)="run()">Run export</button>
        </div>
      </div>

      <mat-table [dataSource]="queue()" class="queue-table">
        <ng-container matColumnDef="journalNumber">
          <mat-header-cell *matHeaderCellDef>Journal</mat-header-cell>
          <mat-cell *matCellDef="let r">{{ r.journalNumber }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="status">
          <mat-header-cell *matHeaderCellDef>Status</mat-header-cell>
          <mat-cell *matCellDef="let r"
            ><mat-chip>{{ r.status }}</mat-chip></mat-cell
          >
        </ng-container>
        <ng-container matColumnDef="exportAttempt">
          <mat-header-cell *matHeaderCellDef>Attempts</mat-header-cell>
          <mat-cell *matCellDef="let r">{{ r.exportAttempt }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="exportedAt">
          <mat-header-cell *matHeaderCellDef>Exported</mat-header-cell>
          <mat-cell *matCellDef="let r">{{ r.exportedAt ? (r.exportedAt | istDate: 'mediumDate') : '—' }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="lastError">
          <mat-header-cell *matHeaderCellDef>Error</mat-header-cell>
          <mat-cell *matCellDef="let r" class="error-cell">{{ r.lastError || '—' }}</mat-cell>
        </ng-container>
        <mat-header-row *matHeaderRowDef="columns"></mat-header-row>
        <mat-row *matRowDef="let r; columns: columns"></mat-row>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" colspan="5" style="text-align:center;padding:24px">Queue is empty</td>
        </tr>
      </mat-table>
    </div>
  `,
  styles: [
    `
      .page-container {
        padding: 24px;
      }
      .page-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 16px;
      }
      .actions {
        display: flex;
        gap: 8px;
      }
      .queue-table {
        width: 100%;
      }
      .error-cell {
        color: #c62828;
        font-size: 12px;
      }
    `,
  ],
})
export class BusyExportComponent implements OnInit {
  columns = ['journalNumber', 'status', 'exportAttempt', 'exportedAt', 'lastError'];
  queue = signal<QueueRow[]>([]);

  constructor(
    private http: HttpClient,
    private snack: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.http.get<QueueRow[]>('/api/integration/busy-export/queue').subscribe((d) => this.queue.set(d));
  }

  stage(): void {
    this.http.post<ExportResult>('/api/integration/busy-export/stage', {}).subscribe((r) => {
      this.snack.open(`Staged ${r.queued} journals`, 'OK', { duration: 3000 });
      this.load();
    });
  }

  run(): void {
    this.http.post<ExportResult>('/api/integration/busy-export/run', {}).subscribe((r) => {
      this.snack.open(`Exported: ${r.exported}, Failed: ${r.failed}`, 'OK', { duration: 4000 });
      this.load();
    });
  }
}
