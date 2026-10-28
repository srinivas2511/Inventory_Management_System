import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';

export interface OpenItemDto {
  id: number;
  itemType: string;
  partyType: string;
  partyId: number;
  sourceType: string;
  sourceId: number | null;
  docNo: string;
  docDate: string | null;
  dueDate: string | null;
  currency: string;
  amount: number;
  amountInr: number;
  balance: number;
  balanceInr: number;
  ageBucket: string;
}

@Component({
  selector: 'app-open-item-list',
  standalone: true,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatSnackBarModule,
    IstDatePipe,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.item-open-items">Open Items</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="items()" class="mat-elevation-z2 full-width">
        <ng-container matColumnDef="docNo">
          <th mat-header-cell *matHeaderCellDef>Doc No</th>
          <td mat-cell *matCellDef="let row">{{ row.docNo }}</td>
        </ng-container>
        <ng-container matColumnDef="itemType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let row">{{ row.itemType }}</td>
        </ng-container>
        <ng-container matColumnDef="partyType">
          <th mat-header-cell *matHeaderCellDef>Party</th>
          <td mat-cell *matCellDef="let row">{{ row.partyType }}</td>
        </ng-container>
        <ng-container matColumnDef="dueDate">
          <th mat-header-cell *matHeaderCellDef>Due Date</th>
          <td mat-cell *matCellDef="let row">{{ row.dueDate | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="ageBucket">
          <th mat-header-cell *matHeaderCellDef>Ageing</th>
          <td mat-cell *matCellDef="let row"><app-status-badge [status]="row.ageBucket" /></td>
        </ng-container>
        <ng-container matColumnDef="amountInr">
          <th mat-header-cell *matHeaderCellDef>Amount (INR)</th>
          <td mat-cell *matCellDef="let row">{{ row.amountInr | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="balanceInr">
          <th mat-header-cell *matHeaderCellDef>Balance (INR)</th>
          <td mat-cell *matCellDef="let row">{{ row.balanceInr | number: '1.2-2' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class OpenItemListComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  columns = ['docNo', 'itemType', 'partyType', 'dueDate', 'ageBucket', 'amountInr', 'balanceInr'];
  items = signal<OpenItemDto[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.http.get<OpenItemDto[]>('/api/finance/open-items').subscribe({
      next: (data) => {
        this.items.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load open items', 'Close', { duration: 3000 });
      },
    });
  }
}
