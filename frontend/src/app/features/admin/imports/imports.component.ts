import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface ImportJobDto {
  id: number;
  entityType: string;
  fileName: string;
  status: 'QUEUED' | 'PROCESSING' | 'DONE' | 'FAILED' | 'CANCELLED';
  totalRows: number;
  processedRows: number;
  failedRows: number;
  createdAt: string;
  completedAt: string | null;
}

@Component({
  selector: 'app-imports',
  standalone: true,
  imports: [
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2" i18n="@@imports.title">Data Imports</h2>
      <label mat-flat-button color="primary" class="upload-btn" aria-label="Upload CSV file">
        <mat-icon aria-hidden="true">upload</mat-icon> Upload CSV
        <input type="file" accept=".csv" (change)="onFileSelected($event)" style="display:none" />
      </label>
    </div>

    @if (loading()) {
      <mat-spinner diameter="32"></mat-spinner>
    } @else {
      <table mat-table [dataSource]="jobs()" class="full-width">
        <ng-container matColumnDef="fileName">
          <th mat-header-cell *matHeaderCellDef>File</th>
          <td mat-cell *matCellDef="let row">{{ row.fileName }}</td>
        </ng-container>
        <ng-container matColumnDef="entityType">
          <th mat-header-cell *matHeaderCellDef>Entity</th>
          <td mat-cell *matCellDef="let row">{{ row.entityType }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let row">
            <mat-chip [color]="chipColor(row.status)" highlighted>{{ row.status }}</mat-chip>
          </td>
        </ng-container>
        <ng-container matColumnDef="rows">
          <th mat-header-cell *matHeaderCellDef>Rows</th>
          <td mat-cell *matCellDef="let row">
            {{ row.processedRows }}/{{ row.totalRows }}
            @if (row.failedRows > 0) {
              <span class="fail-count">({{ row.failedRows }} failed)</span>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            @if (row.status === 'QUEUED') {
              <button
                mat-icon-button
                color="warn"
                (click)="cancel(row)"
                aria-label="Cancel import job"
                matTooltip="Cancel"
              >
                <mat-icon aria-hidden="true">cancel</mat-icon>
              </button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let row; columns: cols"></tr>
      </table>
    }
  `,
  styles: [
    `
      .page-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 16px;
      }
      .upload-btn {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        cursor: pointer;
        background: var(--mdc-filled-button-container-color, #6750a4);
        color: #fff;
        padding: 6px 16px;
        border-radius: 4px;
      }
      .full-width {
        width: 100%;
      }
      .fail-count {
        color: var(--mat-warn-color, #b00020);
        font-size: 0.85em;
        margin-left: 4px;
      }
    `,
  ],
})
export class ImportsComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);

  jobs = signal<ImportJobDto[]>([]);
  loading = signal(false);
  cols = ['fileName', 'entityType', 'status', 'rows', 'actions'];

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.http.get<ImportJobDto[]>('/api/import').subscribe({
      next: (data) => {
        this.jobs.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load imports', 'Close', { duration: 3000 });
      },
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const fd = new FormData();
    fd.append('file', file);
    this.http.post<ImportJobDto>('/api/import', fd).subscribe({
      next: (job) => {
        this.jobs.update((arr) => [job, ...arr]);
        this.snack.open('Import queued', 'Close', { duration: 2000 });
        input.value = '';
      },
      error: () => {
        this.snack.open('Upload failed', 'Close', { duration: 3000 });
        input.value = '';
      },
    });
  }

  cancel(job: ImportJobDto) {
    this.http.post<void>(`/api/import/${job.id}/cancel`, {}).subscribe({
      next: () => {
        this.jobs.update((arr) => arr.map((j) => (j.id === job.id ? { ...j, status: 'CANCELLED' } : j)));
        this.snack.open('Import cancelled', 'Close', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to cancel', 'Close', { duration: 3000 }),
    });
  }

  chipColor(status: string): string {
    switch (status) {
      case 'DONE':
        return 'primary';
      case 'FAILED':
        return 'warn';
      case 'PROCESSING':
        return 'accent';
      default:
        return '';
    }
  }
}
