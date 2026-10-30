import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

export interface BelowMinimumDto {
  materialId: number;
  materialCode: string;
  name: string;
  minStock: number;
  onHand: number;
  uom: string;
  shortage: number;
}

@Component({
  selector: 'app-stock-alerts',
  standalone: true,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatSnackBarModule,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.item-stock-alerts">Stock Alerts — Below Minimum</h2>
      <button mat-flat-button color="primary" (click)="load()">
        <mat-icon aria-hidden="true">refresh</mat-icon>
        Refresh
      </button>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else if (items().length === 0) {
      <p>All materials are above minimum stock levels.</p>
    } @else {
      <table mat-table [dataSource]="items()" class="mat-elevation-z2 full-width">
        <ng-container matColumnDef="materialCode">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let row">{{ row.materialCode }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Material</th>
          <td mat-cell *matCellDef="let row">{{ row.name }}</td>
        </ng-container>
        <ng-container matColumnDef="onHand">
          <th mat-header-cell *matHeaderCellDef>On Hand</th>
          <td mat-cell *matCellDef="let row">{{ row.onHand | number: '1.3-3' }} {{ row.uom }}</td>
        </ng-container>
        <ng-container matColumnDef="minStock">
          <th mat-header-cell *matHeaderCellDef>Min Stock</th>
          <td mat-cell *matCellDef="let row">{{ row.minStock | number: '1.3-3' }} {{ row.uom }}</td>
        </ng-container>
        <ng-container matColumnDef="shortage">
          <th mat-header-cell *matHeaderCellDef>Shortage</th>
          <td mat-cell *matCellDef="let row" style="color: #b45309; font-weight: 600;">
            {{ row.shortage | number: '1.3-3' }} {{ row.uom }}
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class StockAlertsComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);

  readonly columns = ['materialCode', 'name', 'onHand', 'minStock', 'shortage'];
  readonly items = signal<BelowMinimumDto[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.http.get<BelowMinimumDto[]>('/api/inventory/below-minimum').subscribe({
      next: (data) => {
        this.items.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.snack.open('Failed to load stock alerts', 'Close', { duration: 3000 });
        this.loading.set(false);
      },
    });
  }
}
