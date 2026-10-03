import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { CrmApi, ReportResult } from './crm.api';

/** Escapes one CSV field: quote when it holds a comma, quote or newline, and double any quotes. */
export function csvField(value: unknown): string {
  const text = value === null || value === undefined ? '' : String(value);
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function toCsv(headers: string[], rows: Record<string, unknown>[]): string {
  const lines = [headers.map(csvField).join(',')];
  for (const row of rows) lines.push(headers.map((h) => csvField(row[h])).join(','));
  return lines.join('\n');
}

/** Opportunities by stage with value, weighted value and expected close range (CRM-005). */
@Component({
  selector: 'app-crm-pipeline-report',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Sales Pipeline</h2>
    </div>
    <p class="hint">Filter by expected close date. Weighted value is estimated value times stage probability.</p>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Expected close from</mat-label>
        <input matInput type="date" [formControl]="fromCtrl" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Expected close to</mat-label>
        <input matInput type="date" [formControl]="toCtrl" />
      </mat-form-field>
      <button mat-raised-button color="primary" [disabled]="running()" (click)="run()">
        <mat-icon>play_arrow</mat-icon> Run
      </button>
      <button mat-stroked-button [disabled]="!result()" (click)="exportCsv()">
        <mat-icon>download</mat-icon> Export CSV
      </button>
    </div>

    @if (running()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    }

    @if (result(); as r) {
      <table mat-table [dataSource]="r.rows" class="mat-elevation-z1">
        @for (col of r.headers; track col) {
          <ng-container [matColumnDef]="col">
            <th mat-header-cell *matHeaderCellDef>{{ col }}</th>
            <td mat-cell *matCellDef="let row">{{ row[col] }}</td>
          </ng-container>
        }
        <tr mat-header-row *matHeaderRowDef="r.headers"></tr>
        <tr mat-row *matRowDef="let row; columns: r.headers"></tr>
      </table>
    }
  `,
  styles: `
    .filter-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .hint {
      opacity: 0.7;
      margin: 0 0 8px;
    }
  `,
})
export class CrmPipelineReportComponent {
  private api = inject(CrmApi);
  private snack = inject(MatSnackBar);

  result = signal<ReportResult | null>(null);
  running = signal(false);
  fromCtrl = new FormControl<string | null>(null);
  toCtrl = new FormControl<string | null>(null);

  run(): void {
    this.running.set(true);
    this.api.runPipelineReport(this.fromCtrl.value ?? undefined, this.toCtrl.value ?? undefined).subscribe({
      next: (r) => {
        this.result.set(r);
        this.running.set(false);
      },
      error: () => {
        this.running.set(false);
        this.snack.open('Report failed to run', 'Dismiss', { duration: 3000 });
      },
    });
  }

  exportCsv(): void {
    const r = this.result();
    if (!r) return;
    const blob = new Blob([toCsv(r.headers, r.rows)], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'crm-pipeline.csv';
    a.click();
    URL.revokeObjectURL(url);
  }
}
