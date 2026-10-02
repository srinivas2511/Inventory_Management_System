import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';
import { ReasonDialogComponent, ReasonDialogData } from './reason-dialog.component';

/** Promise-based wrappers so a screen can write {@code if (await dialogs.confirm(...))}. */
@Injectable({ providedIn: 'root' })
export class DialogService {
  private readonly dialog = inject(MatDialog);

  /** True if the user confirmed. */
  async confirm(data: ConfirmDialogData): Promise<boolean> {
    const result = await firstValueFrom(
      this.dialog
        .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, { data, width: '420px' })
        .afterClosed(),
    );
    return result === true;
  }

  /** The reason text, or null if the user cancelled. An optional reason may be the empty string. */
  async askReason(data: ReasonDialogData): Promise<string | null> {
    const result = await firstValueFrom(
      this.dialog
        .open<ReasonDialogComponent, ReasonDialogData, string | undefined>(ReasonDialogComponent, {
          data,
          width: '460px',
        })
        .afterClosed(),
    );
    return result === undefined ? null : result;
  }
}
