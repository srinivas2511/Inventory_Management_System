import { Component, OnInit, inject, signal } from '@angular/core';

import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import {
  DOCUMENT_STATUSES,
  DocumentCategoryDto,
  DocumentStatus,
  DocumentSummaryDto,
  DocumentsApi,
} from './documents.api';

@Component({
  selector: 'app-document-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    IstDatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Documents</h2>
      <a *appHasPermission="'DOCUMENT_UPLOAD'" mat-flat-button color="primary" routerLink="/documents/new">
        <mat-icon>upload_file</mat-icon> Upload document
      </a>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Category</mat-label>
        <mat-select [formControl]="categoryCtrl" (selectionChange)="load()">
          <mat-option [value]="null">All</mat-option>
          @for (c of categories(); track c.categoryCode) {
            <mat-option [value]="c.categoryCode">{{ c.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl" (selectionChange)="load()">
          <mat-option [value]="null">All</mat-option>
          @for (s of statuses; track s) {
            <mat-option [value]="s">{{ s }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Search</mat-label>
        <input matInput [formControl]="searchCtrl" (keyup.enter)="load()" placeholder="Number or title" />
      </mat-form-field>
      <button mat-stroked-button type="button" (click)="load()"><mat-icon>search</mat-icon> Search</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="documents()" class="mat-elevation-z1">
        <ng-container matColumnDef="documentNumber">
          <th mat-header-cell *matHeaderCellDef>Number</th>
          <td mat-cell *matCellDef="let r">{{ r.documentNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="title">
          <th mat-header-cell *matHeaderCellDef>Title</th>
          <td mat-cell *matCellDef="let r">{{ r.title }}</td>
        </ng-container>
        <ng-container matColumnDef="category">
          <th mat-header-cell *matHeaderCellDef>Category</th>
          <td mat-cell *matCellDef="let r">{{ r.categoryName }}</td>
        </ng-container>
        <ng-container matColumnDef="version">
          <th mat-header-cell *matHeaderCellDef>Version</th>
          <td mat-cell *matCellDef="let r">v{{ r.currentVersionNo }}</td>
        </ng-container>
        <ng-container matColumnDef="links">
          <th mat-header-cell *matHeaderCellDef>Linked to</th>
          <td mat-cell *matCellDef="let r">{{ linkSummary(r) }}</td>
        </ng-container>
        <ng-container matColumnDef="updated">
          <th mat-header-cell *matHeaderCellDef>Updated</th>
          <td mat-cell *matCellDef="let r">{{ r.updatedAt | istDate: 'dd MMM yyyy HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row)"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No documents found
          </td>
        </tr>
      </table>
    }
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    .filter-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .clickable {
      cursor: pointer;
    }
  `,
})
export class DocumentListComponent implements OnInit {
  private readonly api = inject(DocumentsApi);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);

  readonly statuses = DOCUMENT_STATUSES;
  readonly columns = ['documentNumber', 'title', 'category', 'version', 'links', 'updated', 'status'];
  readonly categoryCtrl = new FormControl<string | null>(null);
  readonly statusCtrl = new FormControl<DocumentStatus | null>(null);
  readonly searchCtrl = new FormControl<string>('', { nonNullable: true });

  readonly documents = signal<DocumentSummaryDto[]>([]);
  readonly categories = signal<DocumentCategoryDto[]>([]);
  readonly loading = signal(false);

  ngOnInit(): void {
    this.api.categories().subscribe({ next: (c) => this.categories.set(c), error: () => undefined });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api
      .list({
        q: this.searchCtrl.value.trim() || null,
        category: this.categoryCtrl.value,
        status: this.statusCtrl.value,
      })
      .subscribe({
        next: (p) => {
          this.documents.set(p.content);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.snack.open('Could not load documents', 'Close', { duration: 4000 });
        },
      });
  }

  linkSummary(d: DocumentSummaryDto): string {
    return d.links.length === 0 ? '—' : d.links.map((l) => `${l.entityType} #${l.entityId}`).join(', ');
  }

  open(d: DocumentSummaryDto): void {
    void this.router.navigate(['/documents', d.id]);
  }
}
