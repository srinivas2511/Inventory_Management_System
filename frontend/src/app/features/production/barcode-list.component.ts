import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';

export interface BarcodeLabelDto {
  id: number;
  labelType: string;
  refType: string;
  refId: number;
  barcodeValue: string;
  qrData: string | null;
  printedAt: string | null;
  printCount: number;
  createdAt: string | null;
}

@Component({
  selector: 'app-barcode-list',
  standalone: true,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatSnackBarModule,
    IstDatePipe,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.item-barcodes">Barcode Labels</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="labels()" class="mat-elevation-z2 full-width">
        <ng-container matColumnDef="id">
          <th mat-header-cell *matHeaderCellDef>#</th>
          <td mat-cell *matCellDef="let row">{{ row.id }}</td>
        </ng-container>
        <ng-container matColumnDef="labelType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let row">{{ row.labelType }}</td>
        </ng-container>
        <ng-container matColumnDef="refType">
          <th mat-header-cell *matHeaderCellDef>Ref Type</th>
          <td mat-cell *matCellDef="let row">{{ row.refType }}</td>
        </ng-container>
        <ng-container matColumnDef="refId">
          <th mat-header-cell *matHeaderCellDef>Ref ID</th>
          <td mat-cell *matCellDef="let row">{{ row.refId }}</td>
        </ng-container>
        <ng-container matColumnDef="barcodeValue">
          <th mat-header-cell *matHeaderCellDef>Barcode</th>
          <td mat-cell *matCellDef="let row">{{ row.barcodeValue }}</td>
        </ng-container>
        <ng-container matColumnDef="printCount">
          <th mat-header-cell *matHeaderCellDef>Prints</th>
          <td mat-cell *matCellDef="let row">{{ row.printCount | number }}</td>
        </ng-container>
        <ng-container matColumnDef="printedAt">
          <th mat-header-cell *matHeaderCellDef>Last Printed</th>
          <td mat-cell *matCellDef="let row">{{ row.printedAt | istDate: 'short' }}</td>
        </ng-container>
        <ng-container matColumnDef="createdAt">
          <th mat-header-cell *matHeaderCellDef>Created</th>
          <td mat-cell *matCellDef="let row">{{ row.createdAt | istDate: 'short' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let row">
            <button mat-icon-button aria-label="Print barcode label" (click)="printLabel(row.id)">
              <mat-icon aria-hidden="true">print</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class BarcodeListComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);

  readonly columns = ['id', 'labelType', 'refType', 'refId', 'barcodeValue', 'printCount', 'printedAt', 'createdAt', 'actions'];
  readonly labels = signal<BarcodeLabelDto[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.http.get<BarcodeLabelDto[]>('/api/production/barcodes').subscribe({
      next: (data) => {
        this.labels.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.snack.open('Failed to load barcodes', 'Close', { duration: 3000 });
        this.loading.set(false);
      },
    });
  }

  printLabel(id: number): void {
    this.http.get(`/api/production/barcodes/${id}/label`, { responseType: 'text' }).subscribe({
      next: (html) => {
        const win = window.open('', '_blank');
        if (win) {
          win.document.write(html);
          win.document.close();
          win.focus();
          win.print();
        }
      },
      error: () => this.snack.open('Failed to load label', 'Close', { duration: 3000 }),
    });
  }
}
