import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { DocumentAttachmentsComponent } from './document-attachments.component';

export interface DocumentsDialogData {
  entityType: string;
  entityId: number;
  title: string;
  defaultCategory?: string | null;
}

/** The documents attached to one record, in a dialog; for list screens that have no detail page of their own. */
@Component({
  selector: 'app-documents-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, DocumentAttachmentsComponent],
  template: `
    <h2 mat-dialog-title>Documents — {{ data.title }}</h2>
    <mat-dialog-content>
      <app-document-attachments
        [entityType]="data.entityType"
        [entityId]="data.entityId"
        [defaultCategory]="data.defaultCategory ?? null"
      />
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
})
export class DocumentsDialogComponent {
  protected readonly data = inject<DocumentsDialogData>(MAT_DIALOG_DATA);
}

export function openDocumentsDialog(
  dialog: MatDialog,
  data: DocumentsDialogData,
): MatDialogRef<DocumentsDialogComponent> {
  return dialog.open(DocumentsDialogComponent, { data, width: '760px', maxWidth: '95vw' });
}
