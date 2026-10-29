import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { HttpClient } from '@angular/common/http';

interface MachineLoadRow {
  machineId: number;
  machineCode: string;
  machineName: string;
  capacityDate: string;
  availableHours: number;
  scheduledHours: number;
  loadPct: number;
}

interface CapacityBoardDto {
  machineLoad: MachineLoadRow[];
  scheduledOps: unknown[];
}

@Component({
  selector: 'app-capacity-board',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressBarModule,
  ],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title i18n="@@capacityBoard.title">Capacity Board</mat-card-title>
        <mat-card-subtitle i18n="@@capacityBoard.subtitle">Machine load and scheduled operations</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="load()" class="filter-row">
          <mat-form-field appearance="outline">
            <mat-label i18n="@@capacityBoard.from">From</mat-label>
            <input matInput [matDatepicker]="dp1" formControlName="from" />
            <mat-datepicker-toggle matIconSuffix [for]="dp1"></mat-datepicker-toggle>
            <mat-datepicker #dp1></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label i18n="@@capacityBoard.to">To</mat-label>
            <input matInput [matDatepicker]="dp2" formControlName="to" />
            <mat-datepicker-toggle matIconSuffix [for]="dp2"></mat-datepicker-toggle>
            <mat-datepicker #dp2></mat-datepicker>
          </mat-form-field>
          <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid">
            <mat-icon aria-hidden="true">search</mat-icon>
            <span i18n="@@capacityBoard.load">Load</span>
          </button>
        </form>

        @if (loading) {
          <div class="center"><mat-spinner diameter="40"></mat-spinner></div>
        } @else if (rows.length === 0) {
          <p class="empty" i18n="@@capacityBoard.empty">No capacity records found for the selected range.</p>
        } @else {
          <table mat-table [dataSource]="rows" class="full-width">
            <ng-container matColumnDef="machine">
              <th mat-header-cell *matHeaderCellDef i18n="@@capacityBoard.col.machine">Machine</th>
              <td mat-cell *matCellDef="let r">{{ r.machineCode }} – {{ r.machineName }}</td>
            </ng-container>
            <ng-container matColumnDef="date">
              <th mat-header-cell *matHeaderCellDef i18n="@@capacityBoard.col.date">Date</th>
              <td mat-cell *matCellDef="let r">{{ r.capacityDate }}</td>
            </ng-container>
            <ng-container matColumnDef="available">
              <th mat-header-cell *matHeaderCellDef i18n="@@capacityBoard.col.available">Available hrs</th>
              <td mat-cell *matCellDef="let r">{{ r.availableHours | number: '1.1-1' }}</td>
            </ng-container>
            <ng-container matColumnDef="scheduled">
              <th mat-header-cell *matHeaderCellDef i18n="@@capacityBoard.col.scheduled">Scheduled hrs</th>
              <td mat-cell *matCellDef="let r">{{ r.scheduledHours | number: '1.1-1' }}</td>
            </ng-container>
            <ng-container matColumnDef="load">
              <th mat-header-cell *matHeaderCellDef i18n="@@capacityBoard.col.load">Load %</th>
              <td mat-cell *matCellDef="let r">
                <mat-progress-bar
                  mode="determinate"
                  [value]="r.loadPct"
                  [color]="r.loadPct > 90 ? 'warn' : 'primary'"
                ></mat-progress-bar>
                {{ r.loadPct | number: '1.1-1' }}%
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [
    `
      .filter-row {
        display: flex;
        gap: 1rem;
        align-items: center;
        flex-wrap: wrap;
        margin-bottom: 1rem;
      }
      .center {
        display: flex;
        justify-content: center;
        padding: 2rem;
      }
      .empty {
        color: var(--mat-sys-on-surface-variant);
        padding: 1rem 0;
      }
      .full-width {
        width: 100%;
      }
      mat-progress-bar {
        width: 80px;
        display: inline-flex;
        vertical-align: middle;
        margin-right: 4px;
      }
    `,
  ],
})
export class CapacityBoardComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);

  rows: MachineLoadRow[] = [];
  loading = false;
  readonly columns = ['machine', 'date', 'available', 'scheduled', 'load'];

  readonly form = this.fb.group({
    from: [this.defaultFrom(), Validators.required],
    to: [this.defaultTo(), Validators.required],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    if (this.form.invalid) return;
    const from = this.fmt(this.form.value.from as Date);
    const to = this.fmt(this.form.value.to as Date);
    this.loading = true;
    this.http.get<CapacityBoardDto>(`/api/production/capacity-board?from=${from}&to=${to}`).subscribe({
      next: (d) => {
        this.rows = d.machineLoad;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  private defaultFrom(): Date {
    const d = new Date();
    d.setDate(1);
    return d;
  }
  private defaultTo(): Date {
    const d = new Date();
    d.setMonth(d.getMonth() + 1, 0);
    return d;
  }
  private fmt(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
