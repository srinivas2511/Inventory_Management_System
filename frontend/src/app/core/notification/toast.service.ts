import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** Toast notifications for outcomes (UI/UX requirement: toast notifications). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.open(message, 'toast-success', 3500);
  }

  info(message: string): void {
    this.open(message, 'toast-info', 4000);
  }

  error(message: string): void {
    this.open(message, 'toast-error', 8000);
  }

  private open(message: string, panelClass: string, duration: number): void {
    this.snackBar.open(message, 'Close', {
      duration,
      panelClass,
      horizontalPosition: 'end',
      verticalPosition: 'bottom',
    });
  }
}
