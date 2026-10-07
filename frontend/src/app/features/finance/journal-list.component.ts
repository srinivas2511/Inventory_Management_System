import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { FinanceApi, JournalHeaderDto } from './finance.api';

@Component({
  selector: 'app-journal-list',
  standalone: true,
  imports: [
    HasPermissionDirective,
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Journal Entries</h2>
      <div class="header-actions">
        <a mat-flat-button color="primary" *appHasPermission="'GL_POST'" routerLink="/finance/journals/new">
          <mat-icon>add</mat-icon> New journal
        </a>
        <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
      </div>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="journals()" class="mat-elevation-z1">
        <ng-container matColumnDef="journalNumber">
          <th mat-header-cell *matHeaderCellDef>Number</th>
          <td mat-cell *matCellDef="let r">
            <a [routerLink]="['/finance/journals', r.id]">{{ r.journalNumber }}</a>
          </td>
        </ng-container>
        <ng-container matColumnDef="journalType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">{{ r.journalType }}</td>
        </ng-container>
        <ng-container matColumnDef="postingDate">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let r">{{ r.postingDate }}</td>
        </ng-container>
        <ng-container matColumnDef="description">
          <th mat-header-cell *matHeaderCellDef>Description</th>
          <td mat-cell *matCellDef="let r">{{ r.description }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
  styles: [
    `
      .header-actions {
        display: flex;
        gap: 8px;
      }
    `,
  ],
})
export class JournalListComponent implements OnInit {
  private api = inject(FinanceApi);

  columns = ['journalNumber', 'journalType', 'postingDate', 'description', 'status'];
  journals = signal<JournalHeaderDto[]>([]);
  loading = signal(false);

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.api.getJournals().subscribe({
      next: (p) => {
        this.journals.set(p.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
