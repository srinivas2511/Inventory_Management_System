import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';

interface ShipmentTracking {
  id: number;
  dispatchId: number;
  courierCode: string;
  trackingNumber: string;
  trackingUrl: string;
  status: string;
  lastCheckpoint: string | null;
  lastUpdated: string | null;
  createdAt: string;
}

@Component({
  selector: 'app-shipment-tracking',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule, MatChipsModule, IstDatePipe],
  template: `
    <div class="page-container">
      <h2 i18n="@@nav.item-shipment-tracking">Shipment Tracking</h2>
      <mat-table [dataSource]="trackings()" class="tracking-table">
        <ng-container matColumnDef="courierCode">
          <mat-header-cell *matHeaderCellDef>Courier</mat-header-cell>
          <mat-cell *matCellDef="let r">{{ r.courierCode }}</mat-cell>
        </ng-container>
        <ng-container matColumnDef="trackingNumber">
          <mat-header-cell *matHeaderCellDef>Tracking No.</mat-header-cell>
          <mat-cell *matCellDef="let r">
            <a [href]="r.trackingUrl" target="_blank" rel="noopener">{{ r.trackingNumber }}</a>
          </mat-cell>
        </ng-container>
        <ng-container matColumnDef="status">
          <mat-header-cell *matHeaderCellDef>Status</mat-header-cell>
          <mat-cell *matCellDef="let r"
            ><mat-chip>{{ r.status }}</mat-chip></mat-cell
          >
        </ng-container>
        <ng-container matColumnDef="lastUpdated">
          <mat-header-cell *matHeaderCellDef>Last Updated</mat-header-cell>
          <mat-cell *matCellDef="let r">{{ r.lastUpdated ? (r.lastUpdated | istDate: 'mediumDate') : '—' }}</mat-cell>
        </ng-container>
        <mat-header-row *matHeaderRowDef="columns"></mat-header-row>
        <mat-row *matRowDef="let r; columns: columns"></mat-row>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" colspan="4" style="text-align:center;padding:24px">No shipment trackings found</td>
        </tr>
      </mat-table>
    </div>
  `,
  styles: [
    `
      .page-container {
        padding: 24px;
      }
      .tracking-table {
        width: 100%;
      }
      a {
        color: inherit;
      }
    `,
  ],
})
export class ShipmentTrackingComponent implements OnInit {
  columns = ['courierCode', 'trackingNumber', 'status', 'lastUpdated'];
  trackings = signal<ShipmentTracking[]>([]);

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get<ShipmentTracking[]>('/api/integration/shipment-tracking').subscribe((d) => this.trackings.set(d));
  }
}
