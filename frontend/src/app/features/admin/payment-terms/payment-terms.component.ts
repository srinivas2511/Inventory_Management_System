import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface PaymentTermDto {
  id: number;
  code: string;
  name: string;
  dueDays: number;
  discountPct: number | null;
  discountDays: number | null;
  active: boolean;
}

@Component({
  selector: 'app-payment-terms',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2" i18n="@@payment-terms.title">Payment Terms</h2>
      <button mat-flat-button color="primary" (click)="startCreate()" [disabled]="creating()">
        <mat-icon aria-hidden="true">add</mat-icon> Add
      </button>
    </div>

    @if (creating()) {
      <form [formGroup]="form" (ngSubmit)="save()" class="inline-form mat-elevation-z2">
        <mat-form-field>
          <mat-label>Code</mat-label>
          <input matInput formControlName="code" placeholder="NET30" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" placeholder="Net 30 Days" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Due Days</mat-label>
          <input matInput type="number" formControlName="dueDays" min="0" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Discount %</mat-label>
          <input matInput type="number" formControlName="discountPct" min="0" max="100" step="0.01" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Discount Days</mat-label>
          <input matInput type="number" formControlName="discountDays" min="0" />
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
      <table mat-table [dataSource]="terms()" class="full-width">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let row">{{ row.code }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let row">{{ row.name }}</td>
        </ng-container>
        <ng-container matColumnDef="dueDays">
          <th mat-header-cell *matHeaderCellDef>Due Days</th>
          <td mat-cell *matCellDef="let row">{{ row.dueDays }}</td>
        </ng-container>
        <ng-container matColumnDef="discountPct">
          <th mat-header-cell *matHeaderCellDef>Discount %</th>
          <td mat-cell *matCellDef="let row">{{ row.discountPct ?? '—' }}</td>
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
export class PaymentTermsComponent implements OnInit {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  terms = signal<PaymentTermDto[]>([]);
  loading = signal(false);
  creating = signal(false);
  cols = ['code', 'name', 'dueDays', 'discountPct'];

  form = this.fb.group({
    code: ['', [Validators.required, Validators.maxLength(20)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    dueDays: [30, [Validators.required, Validators.min(0)]],
    discountPct: [null as number | null],
    discountDays: [null as number | null],
  });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.http.get<PaymentTermDto[]>('/api/admin/payment-terms').subscribe({
      next: (data) => {
        this.terms.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load terms', 'Close', { duration: 3000 });
      },
    });
  }

  startCreate() {
    this.form.reset({ dueDays: 30 });
    this.creating.set(true);
  }

  save() {
    if (this.form.invalid) return;
    this.http.post<PaymentTermDto>('/api/admin/payment-terms', this.form.value).subscribe({
      next: (created) => {
        this.terms.update((arr) => [...arr, created]);
        this.creating.set(false);
        this.snack.open('Term created', 'Close', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to create term', 'Close', { duration: 3000 }),
    });
  }
}
