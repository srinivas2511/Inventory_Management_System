import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

export interface CompanyProfileDto {
  id?: number;
  legalName: string;
  tradeName: string | null;
  gstin: string | null;
  pan: string | null;
  cin: string | null;
  registeredAddress: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
}

@Component({
  selector: 'app-company-profile',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2" i18n="@@company-profile.title">Company Profile</h2>
    </div>

    @if (loading()) {
      <mat-spinner diameter="32"></mat-spinner>
    } @else {
      <form [formGroup]="form" (ngSubmit)="save()" class="profile-form">
        <mat-form-field class="full-width">
          <mat-label>Legal Name</mat-label>
          <input matInput formControlName="legalName" />
        </mat-form-field>
        <mat-form-field class="full-width">
          <mat-label>Trade Name</mat-label>
          <input matInput formControlName="tradeName" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>GSTIN</mat-label>
          <input matInput formControlName="gstin" maxlength="15" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>PAN</mat-label>
          <input matInput formControlName="pan" maxlength="10" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>CIN</mat-label>
          <input matInput formControlName="cin" maxlength="21" />
        </mat-form-field>
        <mat-form-field class="full-width">
          <mat-label>Registered Address</mat-label>
          <textarea matInput formControlName="registeredAddress" rows="3"></textarea>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Phone</mat-label>
          <input matInput formControlName="phone" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Email</mat-label>
          <input matInput formControlName="email" type="email" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Website</mat-label>
          <input matInput formControlName="website" />
        </mat-form-field>
        <div class="form-actions">
          <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving()">
            <mat-icon aria-hidden="true">save</mat-icon> Save Changes
          </button>
        </div>
      </form>
    }
  `,
  styles: [
    `
      .page-header {
        margin-bottom: 16px;
      }
      .profile-form {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
        gap: 12px;
      }
      .full-width {
        grid-column: 1 / -1;
      }
      .form-actions {
        grid-column: 1 / -1;
      }
    `,
  ],
})
export class CompanyProfileComponent implements OnInit {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  loading = signal(false);
  saving = signal(false);

  form = this.fb.group({
    legalName: ['', [Validators.required, Validators.maxLength(200)]],
    tradeName: [''],
    gstin: [''],
    pan: [''],
    cin: [''],
    registeredAddress: [''],
    phone: [''],
    email: [''],
    website: [''],
  });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.http.get<CompanyProfileDto>('/api/admin/company-profile').subscribe({
      next: (data) => {
        this.form.patchValue(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load profile', 'Close', { duration: 3000 });
      },
    });
  }

  save() {
    if (this.form.invalid) return;
    this.saving.set(true);
    this.http.put<CompanyProfileDto>('/api/admin/company-profile', this.form.value).subscribe({
      next: () => {
        this.saving.set(false);
        this.snack.open('Profile saved', 'Close', { duration: 2000 });
      },
      error: () => {
        this.saving.set(false);
        this.snack.open('Failed to save', 'Close', { duration: 3000 });
      },
    });
  }
}
