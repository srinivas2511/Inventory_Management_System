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

export interface TransporterDto {
  id: number;
  code: string;
  name: string;
  gstin: string | null;
  phone: string | null;
  email: string | null;
  active: boolean;
}

@Component({
  selector: 'app-transporters',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2" i18n="@@transporters.title">Transporters</h2>
      <button mat-flat-button color="primary" (click)="startCreate()" [disabled]="creating()">
        <mat-icon aria-hidden="true">add</mat-icon> Add
      </button>
    </div>

    @if (creating()) {
      <form [formGroup]="form" (ngSubmit)="save()" class="inline-form mat-elevation-z2">
        <mat-form-field>
          <mat-label>Code</mat-label>
          <input matInput formControlName="code" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>GSTIN</mat-label>
          <input matInput formControlName="gstin" maxlength="15" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Phone</mat-label>
          <input matInput formControlName="phone" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Email</mat-label>
          <input matInput formControlName="email" type="email" />
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
      <table mat-table [dataSource]="transporters()" class="full-width">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let row">{{ row.code }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let row">{{ row.name }}</td>
        </ng-container>
        <ng-container matColumnDef="gstin">
          <th mat-header-cell *matHeaderCellDef>GSTIN</th>
          <td mat-cell *matCellDef="let row">{{ row.gstin ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="phone">
          <th mat-header-cell *matHeaderCellDef>Phone</th>
          <td mat-cell *matCellDef="let row">{{ row.phone ?? '—' }}</td>
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
export class TransportersComponent implements OnInit {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  transporters = signal<TransporterDto[]>([]);
  loading = signal(false);
  creating = signal(false);
  cols = ['code', 'name', 'gstin', 'phone'];

  form = this.fb.group({
    code: ['', [Validators.required, Validators.maxLength(20)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    gstin: [''],
    phone: [''],
    email: [''],
  });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.http.get<TransporterDto[]>('/api/admin/transporters').subscribe({
      next: (data) => {
        this.transporters.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load', 'Close', { duration: 3000 });
      },
    });
  }

  startCreate() {
    this.form.reset();
    this.creating.set(true);
  }

  save() {
    if (this.form.invalid) return;
    this.http.post<TransporterDto>('/api/admin/transporters', this.form.value).subscribe({
      next: (created) => {
        this.transporters.update((arr) => [...arr, created]);
        this.creating.set(false);
        this.snack.open('Transporter created', 'Close', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to create', 'Close', { duration: 3000 }),
    });
  }
}
