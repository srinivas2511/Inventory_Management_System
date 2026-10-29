import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { IstDatePipe } from '../../../shared/pipes/ist-date.pipe';

interface ScrapLot {
  id: number;
  lotNumber: string;
  sourceType: string;
  itemName: string;
  quantity: number;
  uom: string;
  scrapReason: string;
  status: string;
  salePrice: number | null;
  buyerName: string | null;
  disposedAt: string | null;
  createdAt: string;
}

@Component({
  selector: 'app-scrap-lot-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatDialogModule,
    MatCardModule,
    IstDatePipe,
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <h2 i18n="@@nav.item-scrap-lots">Scrap Lots</h2>
      </div>

      <mat-table [dataSource]="lots()" class="scrap-table">
        <ng-container matColumnDef="lotNumber">
          <mat-header-cell *matHeaderCellDef>Lot No.</mat-header-cell>
          <mat-cell *matCellDef="let row">{{ row.lotNumber }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="itemName">
          <mat-header-cell *matHeaderCellDef>Item</mat-header-cell>
          <mat-cell *matCellDef="let row">{{ row.itemName }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="quantity">
          <mat-header-cell *matHeaderCellDef>Qty</mat-header-cell>
          <mat-cell *matCellDef="let row">{{ row.quantity | number: '1.0-3' }} {{ row.uom }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="status">
          <mat-header-cell *matHeaderCellDef>Status</mat-header-cell>
          <mat-cell *matCellDef="let row">
            <mat-chip [class]="'status-' + row.status.toLowerCase()">{{ row.status }}</mat-chip>
          </mat-cell>
        </ng-container>
        <ng-container matColumnDef="salePrice">
          <mat-header-cell *matHeaderCellDef>Sale Price</mat-header-cell>
          <mat-cell *matCellDef="let row">{{ row.salePrice ? '₹' + (row.salePrice | number: '1.2-2') : '—' }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="disposedAt">
          <mat-header-cell *matHeaderCellDef>Disposed</mat-header-cell>
          <mat-cell *matCellDef="let row">{{
            row.disposedAt ? (row.disposedAt | istDate: 'mediumDate') : '—'
          }}</mat-cell>
        </ng-container>
        <mat-header-row *matHeaderRowDef="columns"></mat-header-row>
        <mat-row *matRowDef="let row; columns: columns"></mat-row>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" colspan="6" style="text-align:center;padding:24px">No scrap lots found</td>
        </tr>
      </mat-table>
    </div>
  `,
  styles: [
    `
      .page-container {
        padding: 24px;
      }
      .page-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 16px;
      }
      .scrap-table {
        width: 100%;
      }
      .status-sold {
        background: #e8f5e9 !important;
      }
      .status-disposed {
        background: #eeeeee !important;
      }
      .status-pending {
        background: #fff3e0 !important;
      }
      .status-listed_for_sale {
        background: #e3f2fd !important;
      }
    `,
  ],
})
export class ScrapLotListComponent implements OnInit {
  columns = ['lotNumber', 'itemName', 'quantity', 'status', 'salePrice', 'disposedAt'];
  lots = signal<ScrapLot[]>([]);

  constructor(
    private http: HttpClient,
    private dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.http.get<ScrapLot[]>('/api/inventory/scrap-lots').subscribe((data) => this.lots.set(data));
  }
}
