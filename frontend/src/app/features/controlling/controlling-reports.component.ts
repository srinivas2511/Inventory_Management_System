import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { ControllingApi, ReportMeta, ReportResult } from './controlling.api';

@Component({
  selector: 'app-controlling-reports',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Controlling Reports</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Report</mat-label>
        <mat-select [formControl]="reportCtrl">
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
      <mat-form-field appearance="outline">
        <mat-label>Cost centre / profit centre / product ID</mat-label>
        <input matInput type="number" [formControl]="entityCtrl" />
      </mat-form-field>
      <button mat-raised-button color="primary" [disabled]="!reportCtrl.value || running()" (click)="run()">
        <mat-icon>play_arrow</mat-icon> Run
      </button>
    </div>

    @if (running()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    }

    @if (result(); as r) {
      <p class="result-meta">{{ r.displayName }} - {{ r.totalRows }} rows</p>
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
    .result-meta {
      opacity: 0.7;
      font-size: 13px;
      margin: 8px 0;
    }
    .table-scroll {
      overflow-x: auto;
    }
  `,
})
export class ControllingReportsComponent implements OnInit {
  private api = inject(ControllingApi);
  private snack = inject(MatSnackBar);

  reports = signal<ReportMeta[]>([]);
  result = signal<ReportResult | null>(null);
  running = signal(false);

  reportCtrl = new FormControl<string | null>(null);
  fromCtrl = new FormControl<string | null>(null);
  toCtrl = new FormControl<string | null>(null);
  entityCtrl = new FormControl<number | null>(null);

  ngOnInit(): void {
    this.api.listControllingReports().subscribe({
      next: (list) => this.reports.set(list),
      error: () => this.snack.open('Failed to load reports', 'Dismiss', { duration: 3000 }),
    });
  }

  run(): void {
    const code = this.reportCtrl.value;
    if (!code) return;
    this.running.set(true);
    this.result.set(null);
    this.api
      .runReport(
        code,
        this.fromCtrl.value ?? undefined,
        this.toCtrl.value ?? undefined,
        this.entityCtrl.value ?? undefined,
      )
      .subscribe({
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
}
