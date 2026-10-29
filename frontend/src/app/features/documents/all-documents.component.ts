import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';

interface DocumentRow {
  docType: string;
  docNumber: string;
  status: string;
  party: string;
  docDate: string;
  navigateTo: string;
}

interface AllDocumentsResponse {
  rows: DocumentRow[];
  totalCount: number;
}

const DOC_TYPES = [
  { value: '', label: 'All Types' },
  { value: 'Purchase Order', label: 'Purchase Orders' },
  { value: 'Goods Receipt', label: 'Goods Receipts' },
  { value: 'Sales Order', label: 'Sales Orders' },
  { value: 'Dispatch', label: 'Dispatches' },
  { value: 'AR Invoice', label: 'AR Invoices' },
  { value: 'AP Invoice', label: 'AP Invoices' },
  { value: 'Production Order', label: 'Production Orders' },
  { value: 'Journal', label: 'Journals' },
];

@Component({
  selector: 'app-all-documents',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatPaginatorModule,
    IstDatePipe,
  ],
  template: `
    <div class="page-container">
      <h2 i18n="@@nav.item-all-documents">All Documents</h2>

      <div class="filters-row">
        <mat-form-field appearance="outline" class="search-field">
          <mat-label>Search document number</mat-label>
          <input matInput [(ngModel)]="search" (ngModelChange)="onFilterChange()" placeholder="e.g. PO-2024-001" />
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>

        <mat-form-field appearance="outline" class="type-field">
          <mat-label>Document type</mat-label>
          <mat-select [(ngModel)]="selectedType" (ngModelChange)="onFilterChange()">
            @for (t of docTypes; track t.value) {
              <mat-option [value]="t.value">{{ t.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </div>

      <mat-table [dataSource]="rows()" class="doc-table">
        <ng-container matColumnDef="docType">
          <mat-header-cell *matHeaderCellDef>Type</mat-header-cell>
          <mat-cell *matCellDef="let row">
            <mat-chip>{{ row.docType }}</mat-chip>
          </mat-cell>
        </ng-container>

        <ng-container matColumnDef="docNumber">
          <mat-header-cell *matHeaderCellDef>Number</mat-header-cell>
          <mat-cell *matCellDef="let row">
            <a [routerLink]="row.navigateTo">{{ row.docNumber }}</a>
          </mat-cell>
        </ng-container>

        <ng-container matColumnDef="status">
          <mat-header-cell *matHeaderCellDef>Status</mat-header-cell>
          <mat-cell *matCellDef="let row">{{ row.status }}</mat-cell>
        </ng-container>

        <ng-container matColumnDef="party">
          <mat-header-cell *matHeaderCellDef>Party</mat-header-cell>
          <mat-cell *matCellDef="let row">{{ row.party }}</mat-cell>
        </ng-container>

        <ng-container matColumnDef="docDate">
          <mat-header-cell *matHeaderCellDef>Date</mat-header-cell>
          <mat-cell *matCellDef="let row">{{ row.docDate | istDate: 'mediumDate' }}</mat-cell>
        </ng-container>

        <mat-header-row *matHeaderRowDef="columns"></mat-header-row>
        <mat-row *matRowDef="let row; columns: columns"></mat-row>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" colspan="5" style="text-align:center;padding:24px">No documents found</td>
        </tr>
      </mat-table>

      <mat-paginator [length]="total()" [pageSize]="pageSize" [pageSizeOptions]="[25, 50, 100]" (page)="onPage($event)">
      </mat-paginator>
    </div>
  `,
  styles: [
    `
      .page-container {
        padding: 24px;
      }
      .filters-row {
        display: flex;
        gap: 16px;
        flex-wrap: wrap;
        margin-bottom: 16px;
      }
      .search-field {
        flex: 2;
        min-width: 240px;
      }
      .type-field {
        flex: 1;
        min-width: 180px;
      }
      .doc-table {
        width: 100%;
      }
      a {
        color: inherit;
        text-decoration: underline;
        cursor: pointer;
      }
    `,
  ],
})
export class AllDocumentsComponent implements OnInit {
  columns = ['docType', 'docNumber', 'status', 'party', 'docDate'];
  docTypes = DOC_TYPES;

  rows = signal<DocumentRow[]>([]);
  total = signal(0);

  search = '';
  selectedType = '';
  page = 0;
  pageSize = 50;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.load();
  }

  onFilterChange(): void {
    this.page = 0;
    this.load();
  }

  onPage(e: PageEvent): void {
    this.page = e.pageIndex;
    this.pageSize = e.pageSize;
    this.load();
  }

  private load(): void {
    const params = new HttpParams()
      .set('docType', this.selectedType)
      .set('search', this.search)
      .set('page', this.page)
      .set('size', this.pageSize);
    this.http.get<AllDocumentsResponse>('/api/documents/all', { params }).subscribe((r) => {
      this.rows.set(r.rows);
      this.total.set(r.totalCount);
    });
  }
}
