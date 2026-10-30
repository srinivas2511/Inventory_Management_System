import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { IstDatePipe } from '../../../shared/pipes/ist-date.pipe';

export interface ApprovalRequestDto {
  id: number;
  documentType: string;
  documentId: number;
  stepNo: number;
  requiredPermission: string | null;
  status: string;
  requestedBy: number;
  decidedBy: number | null;
  decidedAt: string | null;
  comment: string | null;
  createdAt: string;
}

@Component({
  selector: 'app-approval-inbox',
  standalone: true,
  imports: [
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTabsModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    IstDatePipe,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2" i18n="@@approval-inbox.title">Approval Inbox</h2>
    </div>

    <mat-tab-group>
      <mat-tab>
        <ng-template mat-tab-label i18n="@@approval-inbox.tab-pending">Pending</ng-template>
        @if (loadingPending()) {
          <div class="spinner-wrap"><mat-spinner diameter="32"></mat-spinner></div>
        } @else if (pending().length === 0) {
          <p class="empty-msg" i18n="@@approval-inbox.no-pending">No pending approvals.</p>
        } @else {
          <table mat-table [dataSource]="pending()" class="full-table">
            <ng-container matColumnDef="documentType">
              <th mat-header-cell *matHeaderCellDef i18n="@@approval-inbox.col-doc-type">Type</th>
              <td mat-cell *matCellDef="let r">{{ r.documentType }}</td>
            </ng-container>
            <ng-container matColumnDef="documentId">
              <th mat-header-cell *matHeaderCellDef i18n="@@approval-inbox.col-doc-id">Doc #</th>
              <td mat-cell *matCellDef="let r">{{ r.documentId }}</td>
            </ng-container>
            <ng-container matColumnDef="stepNo">
              <th mat-header-cell *matHeaderCellDef i18n="@@approval-inbox.col-step">Step</th>
              <td mat-cell *matCellDef="let r">{{ r.stepNo }}</td>
            </ng-container>
            <ng-container matColumnDef="permission">
              <th mat-header-cell *matHeaderCellDef i18n="@@approval-inbox.col-permission">Permission</th>
              <td mat-cell *matCellDef="let r">{{ r.requiredPermission }}</td>
            </ng-container>
            <ng-container matColumnDef="createdAt">
              <th mat-header-cell *matHeaderCellDef i18n="@@approval-inbox.col-created">Requested</th>
              <td mat-cell *matCellDef="let r">{{ r.createdAt | istDate:'d MMM, HH:mm' }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let r">
                <button mat-icon-button color="primary" (click)="approve(r)"
                        aria-label="Approve" matTooltip="Approve" i18n-matTooltip="@@approval-inbox.approve">
                  <mat-icon aria-hidden="true">check_circle</mat-icon>
                </button>
                <button mat-icon-button color="warn" (click)="reject(r)"
                        aria-label="Reject" matTooltip="Reject" i18n-matTooltip="@@approval-inbox.reject">
                  <mat-icon aria-hidden="true">cancel</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="pendingCols"></tr>
            <tr mat-row *matRowDef="let r; columns: pendingCols"></tr>
          </table>
        }
      </mat-tab>

      <mat-tab>
        <ng-template mat-tab-label i18n="@@approval-inbox.tab-history">History</ng-template>
        @if (loadingHistory()) {
          <div class="spinner-wrap"><mat-spinner diameter="32"></mat-spinner></div>
        } @else if (history().length === 0) {
          <p class="empty-msg" i18n="@@approval-inbox.no-history">No decisions recorded yet.</p>
        } @else {
          <table mat-table [dataSource]="history()" class="full-table">
            <ng-container matColumnDef="documentType">
              <th mat-header-cell *matHeaderCellDef i18n="@@approval-inbox.col-doc-type">Type</th>
              <td mat-cell *matCellDef="let r">{{ r.documentType }}</td>
            </ng-container>
            <ng-container matColumnDef="documentId">
              <th mat-header-cell *matHeaderCellDef i18n="@@approval-inbox.col-doc-id">Doc #</th>
              <td mat-cell *matCellDef="let r">{{ r.documentId }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef i18n="@@approval-inbox.col-status">Decision</th>
              <td mat-cell *matCellDef="let r">
                <mat-chip-row [color]="r.status === 'APPROVED' ? 'primary' : 'warn'"
                              [highlighted]="true" aria-label="r.status">
                  {{ r.status }}
                </mat-chip-row>
              </td>
            </ng-container>
            <ng-container matColumnDef="decidedAt">
              <th mat-header-cell *matHeaderCellDef i18n="@@approval-inbox.col-decided">Decided</th>
              <td mat-cell *matCellDef="let r">{{ r.decidedAt | istDate:'d MMM, HH:mm' }}</td>
            </ng-container>
            <ng-container matColumnDef="comment">
              <th mat-header-cell *matHeaderCellDef i18n="@@approval-inbox.col-comment">Comment</th>
              <td mat-cell *matCellDef="let r">{{ r.comment ?? '—' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="historyCols"></tr>
            <tr mat-row *matRowDef="let r; columns: historyCols"></tr>
          </table>
        }
      </mat-tab>
    </mat-tab-group>
  `,
  styles: [`
    .page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
    .full-table { width: 100%; }
    .spinner-wrap { display: flex; justify-content: center; padding: 32px; }
    .empty-msg { padding: 24px; color: var(--mat-sys-outline); }
  `],
})
export class ApprovalInboxComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);

  pending = signal<ApprovalRequestDto[]>([]);
  history = signal<ApprovalRequestDto[]>([]);
  loadingPending = signal(false);
  loadingHistory = signal(false);

  pendingCols = ['documentType', 'documentId', 'stepNo', 'permission', 'createdAt', 'actions'];
  historyCols = ['documentType', 'documentId', 'status', 'decidedAt', 'comment'];

  ngOnInit() {
    this.loadPending();
    this.loadHistory();
  }

  loadPending() {
    this.loadingPending.set(true);
    this.http.get<ApprovalRequestDto[]>('/api/approvals/pending').subscribe({
      next: (data) => { this.pending.set(data); this.loadingPending.set(false); },
      error: () => { this.loadingPending.set(false); this.snack.open('Failed to load pending approvals', 'Close', { duration: 3000 }); },
    });
  }

  loadHistory() {
    this.loadingHistory.set(true);
    this.http.get<ApprovalRequestDto[]>('/api/approvals/history').subscribe({
      next: (data) => { this.history.set(data); this.loadingHistory.set(false); },
      error: () => { this.loadingHistory.set(false); },
    });
  }

  approve(req: ApprovalRequestDto) {
    this.http.post<ApprovalRequestDto>(`/api/approvals/${req.id}/approve`, {}).subscribe({
      next: () => {
        this.snack.open('Approved', 'Close', { duration: 2000 });
        this.pending.update((arr) => arr.filter((r) => r.id !== req.id));
        this.loadHistory();
      },
      error: () => this.snack.open('Failed to approve', 'Close', { duration: 3000 }),
    });
  }

  reject(req: ApprovalRequestDto) {
    this.http.post<ApprovalRequestDto>(`/api/approvals/${req.id}/reject`, {}).subscribe({
      next: () => {
        this.snack.open('Rejected', 'Close', { duration: 2000 });
        this.pending.update((arr) => arr.filter((r) => r.id !== req.id));
        this.loadHistory();
      },
      error: () => this.snack.open('Failed to reject', 'Close', { duration: 3000 }),
    });
  }
}
