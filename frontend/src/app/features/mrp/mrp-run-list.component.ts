import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { MrpApi, MrpRunDto } from './mrp.api';

@Component({
  selector: 'app-mrp-run-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">MRP Runs</h2>
      <button *appHasPermission="'MRP_RUN'" mat-flat-button color="primary" (click)="starting.set(!starting())">
        <mat-icon>play_arrow</mat-icon> Run MRP
      </button>
    </div>

    @if (starting()) {
      <form class="edit-form" [formGroup]="form" (ngSubmit)="run()">
        <mat-form-field appearance="outline">
          <mat-label>Planning horizon (days)</mat-label>
          <input matInput type="number" min="1" max="730" formControlName="horizonDays" />
        </mat-form-field>
        <mat-checkbox formControlName="includeSafetyStock">Include safety stock</mat-checkbox>
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || running()">Start run</button>
        @if (running()) {
          <mat-spinner diameter="24" />
        }
      </form>
      <p class="hint">Proposals are advisory. Nothing is ordered until you convert a proposal.</p>
    }

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="runs()" class="mat-elevation-z1">
        <ng-container matColumnDef="runNumber">
          <th mat-header-cell *matHeaderCellDef>Run</th>
          <td mat-cell *matCellDef="let r">
            <a [routerLink]="['/mrp/proposals']" [queryParams]="{ runId: r.id }">{{ r.runNumber }}</a>
          </td>
        </ng-container>
        <ng-container matColumnDef="initiatedAt">
          <th mat-header-cell *matHeaderCellDef>Started</th>
          <td mat-cell *matCellDef="let r">{{ r.initiatedAt | date: 'dd MMM yyyy HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="horizon">
          <th mat-header-cell *matHeaderCellDef>Horizon</th>
          <td mat-cell *matCellDef="let r">
            {{ r.planningHorizonDays }} days{{ r.includeSafetyStock ? ' + safety stock' : '' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="proposals">
          <th mat-header-cell *matHeaderCellDef>Proposals</th>
          <td mat-cell *matCellDef="let r">{{ r.totalProposals ?? '-' }}</td>
        </ng-container>
        <ng-container matColumnDef="overloads">
          <th mat-header-cell *matHeaderCellDef>Capacity overloads</th>
          <td mat-cell *matCellDef="let r">{{ r.capacityOverloads ?? '-' }}</td>
        </ng-container>
        <ng-container matColumnDef="notes">
          <th mat-header-cell *matHeaderCellDef>Notes</th>
          <td mat-cell *matCellDef="let r" [title]="r.notes ?? r.errorMessage ?? ''">
            {{ r.errorMessage ?? r.notes ?? '' }}
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No MRP runs yet
          </td>
        </tr>
      </table>
    }
  `,
  styles: `
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .hint {
      opacity: 0.7;
      margin: 0 0 16px;
    }
  `,
})
export class MrpRunListComponent implements OnInit {
  private api = inject(MrpApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  columns = ['runNumber', 'initiatedAt', 'horizon', 'status', 'proposals', 'overloads', 'notes'];
  runs = signal<MrpRunDto[]>([]);
  loading = signal(false);
  starting = signal(false);
  running = signal(false);

  form = this.fb.nonNullable.group({
    horizonDays: [90, [Validators.required, Validators.min(1), Validators.max(730)]],
    includeSafetyStock: [true],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getRuns().subscribe({
      next: (page) => {
        this.runs.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load MRP runs', 'Dismiss', { duration: 3000 });
      },
    });
  }

  run(): void {
    if (this.form.invalid) return;
    this.running.set(true);
    this.api.runMrp(this.form.getRawValue()).subscribe({
      next: (run) => {
        this.running.set(false);
        this.starting.set(false);
        this.snack.open(`${run.runNumber} completed with ${run.totalProposals ?? 0} proposals`, 'OK', {
          duration: 4000,
        });
        this.load();
      },
      error: (err) => {
        this.running.set(false);
        this.snack.open(err?.error?.detail ?? 'MRP run failed', 'Dismiss', { duration: 5000 });
      },
    });
  }
}
