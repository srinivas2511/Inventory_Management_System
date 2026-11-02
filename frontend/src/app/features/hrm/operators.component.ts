import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MatDialog, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface OperatorDto {
  id: number;
  employeeCode: string;
  fullName: string;
  operatorCode?: string;
  designation?: string;
  department?: string;
  active: boolean;
  version: number;
}

// ── Dialog ──────────────────────────────────────────────────────────────────

@Component({
  selector: 'app-operator-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ operator ? 'Edit Operator' : 'New Operator' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="op-form" (ngSubmit)="save()" novalidate class="op-form">
        <mat-form-field appearance="outline" class="full">
          <mat-label>Operator Code</mat-label>
          <input matInput formControlName="operatorCode" />
          @if (form.controls.operatorCode.hasError('required') && form.controls.operatorCode.touched) {
            <mat-error>Required</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Full Name</mat-label>
          <input matInput formControlName="fullName" />
          @if (form.controls.fullName.hasError('required') && form.controls.fullName.touched) {
            <mat-error>Required</mat-error>
          }
        </mat-form-field>
        <mat-slide-toggle formControlName="active">Active</mat-slide-toggle>
        @if (error()) {
          <p class="err mat-body-2">{{ error() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="op-form" type="submit" [disabled]="saving()">
        @if (saving()) { <mat-spinner diameter="18" /> } @else { Save }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .op-form { display: flex; flex-direction: column; gap: 4px; padding: 8px 0; min-width: 320px; }
    .full { width: 100%; }
    .err { color: #f44336; margin-top: 4px; }
  `],
})
export class OperatorFormDialogComponent implements OnInit {
  protected readonly operator = inject<OperatorDto | null>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly form = inject(FormBuilder).nonNullable.group({
    operatorCode: ['', Validators.required],
    fullName: ['', Validators.required],
    active: [true],
  });

  private readonly http = inject(HttpClient);
  private readonly dialogRef = inject(MatDialogRef<OperatorFormDialogComponent>);

  ngOnInit(): void {
    if (this.operator) {
      this.form.patchValue({
        operatorCode: this.operator.operatorCode ?? '',
        fullName: this.operator.fullName,
        active: this.operator.active,
      });
      this.form.controls.operatorCode.disable();
    }
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving.set(true);
    const v = this.form.getRawValue();
    const req = { operatorCode: v.operatorCode, fullName: v.fullName, active: v.active };
    const obs = this.operator
      ? this.http.put<OperatorDto>(`/api/hrm/operators/${this.operator.id}`, req)
      : this.http.post<OperatorDto>('/api/hrm/operators', req);
    obs.subscribe({
      next: () => { this.saving.set(false); this.dialogRef.close(true); },
      error: (err) => { this.saving.set(false); this.error.set(err?.error?.message ?? 'Save failed.'); },
    });
  }
}

// ── List page ────────────────────────────────────────────────────────────────

@Component({
  selector: 'app-operators',
  standalone: true,
  imports: [
    MatTableModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Operators</h2>
      <button mat-flat-button color="primary" (click)="openDialog(null)" aria-label="Add operator">
        <mat-icon aria-hidden="true">add</mat-icon> Add Operator
      </button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="operatorCode">
          <th mat-header-cell *matHeaderCellDef>Operator ID</th>
          <td mat-cell *matCellDef="let r">{{ r.operatorCode ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="employeeCode">
          <th mat-header-cell *matHeaderCellDef>Employee Code</th>
          <td mat-cell *matCellDef="let r">{{ r.employeeCode }}</td>
        </ng-container>
        <ng-container matColumnDef="fullName">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let r">{{ r.fullName }}</td>
        </ng-container>
        <ng-container matColumnDef="designation">
          <th mat-header-cell *matHeaderCellDef>Designation</th>
          <td mat-cell *matCellDef="let r">{{ r.designation ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="department">
          <th mat-header-cell *matHeaderCellDef>Department</th>
          <td mat-cell *matCellDef="let r">{{ r.department ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Active</th>
          <td mat-cell *matCellDef="let r">
            <mat-icon [style.color]="r.active ? 'green' : 'grey'" aria-hidden="true">
              {{ r.active ? 'check_circle' : 'cancel' }}
            </mat-icon>
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button mat-icon-button (click)="openDialog(r)" aria-label="Edit operator" matTooltip="Edit">
              <mat-icon>edit</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (rows().length === 0) {
        <p class="empty">No operators defined yet.</p>
      }
    }
  `,
  styles: [`
    .page-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 16px 0; }
    .loading-center { display: flex; justify-content: center; padding: 48px; }
    table { width: 100%; }
    .empty { padding: 24px 16px; color: #666; }
  `],
})
export class OperatorsComponent implements OnInit {
  protected readonly columns = ['operatorCode', 'employeeCode', 'fullName', 'designation', 'department', 'status', 'actions'];
  protected readonly rows = signal<OperatorDto[]>([]);
  protected readonly loading = signal(false);

  private readonly http = inject(HttpClient);
  private readonly dialog = inject(MatDialog);

  ngOnInit(): void { this.load(); }

  private load(): void {
    this.loading.set(true);
    this.http.get<OperatorDto[]>('/api/hrm/operators').subscribe({
      next: (data) => { this.rows.set(data); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  openDialog(operator: OperatorDto | null): void {
    this.dialog.open(OperatorFormDialogComponent, {
      data: operator,
      width: '420px',
    }).afterClosed().subscribe((saved) => {
      if (saved) this.load();
    });
  }
}
