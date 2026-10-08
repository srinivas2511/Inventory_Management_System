import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

/** Asks for the password again before a sensitive action (SEC-010). The password is sent once and never kept. */
@Component({
  selector: 'app-step-up-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Confirm it is you</h2>
    <mat-dialog-content>
      <p>This action needs you to enter your password again.</p>
      <mat-form-field appearance="outline" class="full">
        <mat-label>Password</mat-label>
        <input
          matInput
          type="password"
          autocomplete="current-password"
          [formControl]="password"
          data-testid="step-up-password"
          (keyup.enter)="confirm()"
        />
        @if (error()) {
          <mat-error>{{ error() }}</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="ref.close(false)">Cancel</button>
      <button
        mat-flat-button
        color="primary"
        type="button"
        [disabled]="password.invalid || busy()"
        data-testid="step-up-confirm"
        (click)="confirm()"
      >
        Confirm
      </button>
    </mat-dialog-actions>
  `,
  styles: ['.full { width: 100%; min-width: 280px; }'],
})
export class StepUpDialogComponent {
  protected readonly ref = inject<MatDialogRef<StepUpDialogComponent, boolean>>(MatDialogRef);
  private readonly http = inject(HttpClient);

  protected readonly password = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected confirm(): void {
    if (this.password.invalid || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    this.http.post('/api/auth/step-up', { credential: this.password.value }).subscribe({
      next: () => this.ref.close(true),
      error: (e: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(e.status === 400 ? 'That password is not correct.' : 'Could not confirm. Try again.');
      },
    });
  }
}
