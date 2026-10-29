import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';

export interface SavedReportDto {
  id: number;
  name: string;
  sourceView: string;
  definition: Record<string, unknown>;
  shareScope: string;
  ownerId: number;
  createdAt: string | null;
}

export interface CatalogueEntry {
  viewName: string;
  columnName: string;
  dataType: string;
  label: string | null;
  aggregations: string | null;
  permissionCode: string | null;
}

@Component({
  selector: 'app-report-builder',
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
      <h2 i18n="@@nav.report_builder">Report Builder</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <div class="report-builder-layout">
        <section>
          <h3>Available Views ({{ catalogue().length }} columns)</h3>
          <table mat-table [dataSource]="catalogue()" class="mat-elevation-z2 full-width">
            <ng-container matColumnDef="viewName">
              <th mat-header-cell *matHeaderCellDef>View</th>
              <td mat-cell *matCellDef="let row">{{ row.viewName }}</td>
            </ng-container>
            <ng-container matColumnDef="columnName">
              <th mat-header-cell *matHeaderCellDef>Column</th>
              <td mat-cell *matCellDef="let row">{{ row.columnName }}</td>
            </ng-container>
            <ng-container matColumnDef="label">
              <th mat-header-cell *matHeaderCellDef>Label</th>
              <td mat-cell *matCellDef="let row">{{ row.label }}</td>
            </ng-container>
            <ng-container matColumnDef="dataType">
              <th mat-header-cell *matHeaderCellDef>Type</th>
              <td mat-cell *matCellDef="let row">{{ row.dataType }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="catalogueCols"></tr>
            <tr mat-row *matRowDef="let row; columns: catalogueCols"></tr>
          </table>
        </section>
        <section>
          <h3>Saved Reports</h3>
          <table mat-table [dataSource]="savedReports()" class="mat-elevation-z2 full-width">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Name</th>
              <td mat-cell *matCellDef="let row">{{ row.name }}</td>
            </ng-container>
            <ng-container matColumnDef="sourceView">
              <th mat-header-cell *matHeaderCellDef>View</th>
              <td mat-cell *matCellDef="let row">{{ row.sourceView }}</td>
            </ng-container>
            <ng-container matColumnDef="shareScope">
              <th mat-header-cell *matHeaderCellDef>Scope</th>
              <td mat-cell *matCellDef="let row">{{ row.shareScope }}</td>
            </ng-container>
            <ng-container matColumnDef="createdAt">
              <th mat-header-cell *matHeaderCellDef>Created</th>
              <td mat-cell *matCellDef="let row">{{ row.createdAt | istDate }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="savedCols"></tr>
            <tr mat-row *matRowDef="let row; columns: savedCols"></tr>
          </table>
        </section>
      </div>
    }
  `,
})
export class ReportBuilderComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  catalogue = signal<CatalogueEntry[]>([]);
  savedReports = signal<SavedReportDto[]>([]);
  loading = signal(true);

  catalogueCols = ['viewName', 'columnName', 'label', 'dataType'];
  savedCols = ['name', 'sourceView', 'shareScope', 'createdAt'];

  ngOnInit(): void {
    this.http.get<CatalogueEntry[]>('/api/report-builder/catalogue').subscribe({
      next: (data) => {
        this.catalogue.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load report catalogue', 'Close', { duration: 3000 });
      },
    });
    this.http.get<SavedReportDto[]>('/api/report-builder/saved').subscribe({
      next: (data) => this.savedReports.set(data),
    });
  }
}
