import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface DocumentSeriesDto {
  id: number;
  documentKind: string;
  seriesCode: string;
  format: string;
  numbering: string;
  resetPolicy: string;
  isDefault: boolean;
  statutory: boolean;
  active: boolean;
}

@Component({
  selector: 'app-document-series',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2" i18n="@@document-series.title">Document Series</h2>
      <button mat-flat-button color="primary" (click)="startCreate()" [disabled]="creating()">
        <mat-icon aria-hidden="true">add</mat-icon> Add Series
      </button>
    </div>

    @if (creating()) {
      <form [formGroup]="form" (ngSubmit)="save()" class="inline-form mat-elevation-z2">
        <mat-form-field>
          <mat-label>Document Kind</mat-label>
          <input matInput formControlName="documentKind" placeholder="SALES_INVOICE" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Series Code</mat-label>
          <input matInput formControlName="seriesCode" placeholder="INV-DEFAULT" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Format</mat-label>
          <input matInput formControlName="format" placeholder="INV-{FY}-{00000}" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Reset Policy</mat-label>
          <mat-select formControlName="resetPolicy">
            <mat-option value="FY">Financial Year</mat-option>
            <mat-option value="MONTH">Monthly</mat-option>
            <mat-option value="NONE">Never</mat-option>
          </mat-select>
        </mat-form-field>
        <div class="form-actions">
          <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">Save</button>
          <button mat-button type="button" (click)="creating.set(false)">Cancel</button>
        </div>
      </form>
    }

    @if (loading()) {
      <mat-spinner diameter="32"></mat-spinner>
    } @else {
      <table mat-table [dataSource]="series()" class="full-width">
        <ng-container matColumnDef="documentKind">
          <th mat-header-cell *matHeaderCellDef>Document Kind</th>
          <td mat-cell *matCellDef="let row">{{ row.documentKind }}</td>
        </ng-container>
        <ng-container matColumnDef="seriesCode">
          <th mat-header-cell *matHeaderCellDef>Series Code</th>
          <td mat-cell *matCellDef="let row">{{ row.seriesCode }}</td>
        </ng-container>
        <ng-container matColumnDef="format">
          <th mat-header-cell *matHeaderCellDef>Format</th>
          <td mat-cell *matCellDef="let row">{{ row.format }}</td>
        </ng-container>
        <ng-container matColumnDef="resetPolicy">
          <th mat-header-cell *matHeaderCellDef>Reset</th>
          <td mat-cell *matCellDef="let row">{{ row.resetPolicy }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            <button
              mat-icon-button
              color="warn"
              aria-label="Deactivate"
              matTooltip="Deactivate"
              (click)="deactivate(row)"
            >
              <mat-icon aria-hidden="true">block</mat-icon>
            </button>
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
      .inline-form {
        padding: 16px;
        margin-bottom: 16px;
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: flex-start;
      }
      .form-actions {
        display: flex;
        gap: 8px;
        align-items: center;
        padding-top: 8px;
      }
      .full-width {
        width: 100%;
      }
    `,
  ],
})
export class DocumentSeriesComponent implements OnInit {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  series = signal<DocumentSeriesDto[]>([]);
  loading = signal(false);
  creating = signal(false);
  cols = ['documentKind', 'seriesCode', 'format', 'resetPolicy', 'actions'];

  form = this.fb.group({
    documentKind: ['', [Validators.required, Validators.maxLength(40)]],
    seriesCode: ['', [Validators.required, Validators.maxLength(20)]],
    format: ['', [Validators.required, Validators.maxLength(80)]],
    numbering: ['AUTO'],
    resetPolicy: ['FY', Validators.required],
    isDefault: [true],
    statutory: [false],
    branchId: [null as number | null],
  });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.http.get<DocumentSeriesDto[]>('/api/admin/document-series').subscribe({
      next: (data) => {
        this.series.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load series', 'Close', { duration: 3000 });
      },
    });
  }

  startCreate() {
    this.form.reset({ numbering: 'AUTO', resetPolicy: 'FY', isDefault: true, statutory: false });
    this.creating.set(true);
  }

  save() {
    if (this.form.invalid) return;
    this.http.post<DocumentSeriesDto>('/api/admin/document-series', this.form.value).subscribe({
      next: (created) => {
        this.series.update((arr) => [...arr, created]);
        this.creating.set(false);
        this.snack.open('Series created', 'Close', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to create series', 'Close', { duration: 3000 }),
    });
  }

  deactivate(row: DocumentSeriesDto) {
    this.http.delete(`/api/admin/document-series/${row.id}`).subscribe({
      next: () => {
        this.series.update((arr) => arr.filter((s) => s.id !== row.id));
        this.snack.open('Series deactivated', 'Close', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to deactivate', 'Close', { duration: 3000 }),
    });
  }
}
