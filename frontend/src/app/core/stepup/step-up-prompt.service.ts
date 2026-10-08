import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable, finalize, map, shareReplay } from 'rxjs';
import { StepUpDialogComponent } from './step-up-dialog.component';

/** Shows the re-authentication dialog. Calls that fail together share one dialog instead of stacking several. */
@Injectable({ providedIn: 'root' })
export class StepUpPromptService {
  private readonly dialog = inject(MatDialog);
  private inFlight: Observable<boolean> | null = null;

  /** Emits true when the user re-authenticated, false when they cancelled. */
  prompt(): Observable<boolean> {
    if (!this.inFlight) {
      this.inFlight = this.dialog
        .open(StepUpDialogComponent, { width: '380px', disableClose: true })
        .afterClosed()
        .pipe(
          map((result) => result === true),
          finalize(() => (this.inFlight = null)),
          shareReplay({ bufferSize: 1, refCount: false }),
        );
    }
    return this.inFlight;
  }
}
