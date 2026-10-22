import { Component, OnInit, inject, signal } from '@angular/core';

import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { InsightApi, ReportMeta, ReportResult } from './insight.api';

@Component({
  selector: 'app-reports',
  standalone: true,
  providers: [InsightApi],
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    IstDatePipe,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Reports</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Report</mat-label>
        <mat-select [formControl]="reportCtrl">
          <mat-option [value]="null">— select —</mat-option>
          @for (r of reports(); track r.code) {
            <mat-option [value]="r.code">{{ r.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>From</mat-label>
        <input matInput type="date" [formControl]="fromCtrl" />
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>To</mat-label>
        <input matInput type="date" [formControl]="toCtrl" />
      </mat-form-field>

      <button mat-raised-button color="primary" [disabled]="!reportCtrl.value || running()" (click)="run()">
        <mat-icon>play_arrow</mat-icon> Run
      </button>
    </div>

    @if (running()) {
      <div class="spinner-center"><mat-spinner diameter="40" /></div>
    }

    @if (result(); as r) {
      <p class="result-meta">{{ r.displayName }} — {{ r.totalRows }} rows</p>
      <div class="table-scroll">
        <table mat-table [dataSource]="r.rows" class="mat-elevation-z1">
          @for (col of r.headers; track col) {
            <ng-container [matColumnDef]="col">
              <th mat-header-cell *matHeaderCellDef>{{ col }}</th>
              <td mat-cell *matCellDef="let row">{{ row[col] }}</td>
            </ng-container>
          }
          <tr mat-header-row *matHeaderRowDef="r.headers; sticky: true"></tr>
          <tr mat-row *matRowDef="let row; columns: r.headers"></tr>
        </table>
      </div>
    }
  `,
  styles: `
    .filter-bar {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
      padding: 8px 0;
      align-items: flex-end;
    }
    .spinner-center {
      display: flex;
      justify-content: center;
      padding: 48px;
    }
    .result-meta {
      color: rgba(0, 0, 0, 0.54);
      font-size: 13px;
      margin: 8px 0;
    }
    .table-scroll {
      overflow-x: auto;
    }
  `,
})
export class ReportsComponent implements OnInit {
  private readonly api = inject(InsightApi);

  protected readonly reports = signal<ReportMeta[]>([]);
  protected readonly result = signal<ReportResult | null>(null);
  protected readonly running = signal(false);

  protected readonly reportCtrl = new FormControl<string | null>(null);
  protected readonly fromCtrl = new FormControl<string | null>(null);
  protected readonly toCtrl = new FormControl<string | null>(null);

  ngOnInit(): void {
    this.api.listReports().subscribe({ next: (r) => this.reports.set(r) });
  }

  protected run(): void {
    const code = this.reportCtrl.value;
    if (!code) return;
    this.running.set(true);
    this.result.set(null);
    this.api.runReport(code, this.fromCtrl.value ?? undefined, this.toCtrl.value ?? undefined).subscribe({
      next: (r) => {
        this.result.set(r);
        this.running.set(false);
      },
      error: () => this.running.set(false),
    });
  }
}
