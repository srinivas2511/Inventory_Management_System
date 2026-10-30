import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

export interface TallyLedgerMappingDto {
  id: number;
  glAccountCode: string;
  tallyLedger: string;
  notes: string;
  createdAt: string;
}

export interface TallyReadinessCheck {
  name: string;
  ok: boolean;
  detail: string;
}

export interface TallyReadinessDto {
  checks: TallyReadinessCheck[];
  ready: boolean;
}

export interface VoucherQueueCountsDto {
  draft: number;
  approved: number;
  sent: number;
  ledgerMissing: number;
  failed: number;
}

// ---------------------------------------------------------------------------
// Readiness Check Dialog
// ---------------------------------------------------------------------------
@Component({
  selector: 'app-tally-readiness-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatButtonModule, MatDialogModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Tally Readiness Check</h2>
    <mat-dialog-content>
      @if (loading()) {
        <p class="loading-msg">Checking readiness&hellip;</p>
      } @else if (error()) {
        <p class="error-msg">{{ error() }}</p>
      } @else if (result()) {
        <ul class="check-list" role="list">
          @for (check of result()!.checks; track check.name) {
            <li class="check-item" [class.ok]="check.ok" [class.fail]="!check.ok">
              <mat-icon [style.color]="check.ok ? '#2e7d32' : '#c62828'" aria-hidden="true">
                {{ check.ok ? 'check_circle' : 'cancel' }}
              </mat-icon>
              <span class="check-name">{{ check.name }}</span>
              <span class="check-detail">{{ check.detail }}</span>
            </li>
          }
        </ul>
        <p class="overall" [class.ready]="result()!.ready" [class.not-ready]="!result()!.ready">
          {{ result()!.ready ? 'Ready for sync' : 'Configuration needed' }}
        </p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .loading-msg { color: #616161; padding: 8px 0; }
    .error-msg   { color: #c62828; padding: 8px 0; }
    .check-list  { list-style: none; padding: 0; margin: 0 0 12px; }
    .check-item  {
      display: grid;
      grid-template-columns: 24px 1fr;
      grid-template-rows: auto auto;
      column-gap: 10px;
      row-gap: 2px;
      padding: 8px 0;
      border-bottom: 1px solid #e0e0e0;
    }
    .check-item:last-child { border-bottom: none; }
    .check-item mat-icon { grid-row: 1 / 3; align-self: center; }
    .check-name  { font-weight: 500; font-size: 13px; }
    .check-detail { font-size: 12px; color: #616161; }
    .overall {
      margin-top: 12px;
      font-weight: 600;
      font-size: 14px;
      text-align: center;
      padding: 8px;
      border-radius: 4px;
    }
    .ready     { background: #e8f5e9; color: #2e7d32; }
    .not-ready { background: #ffebee; color: #c62828; }
  `],
})
export class TallyReadinessDialogComponent {
  private http = inject(HttpClient);

  loading = signal(true);
  error   = signal<string | null>(null);
  result  = signal<TallyReadinessDto | null>(null);

  constructor() {
    this.http.get<TallyReadinessDto>('/api/integration/tally/readiness').subscribe({
      next: (data) => {
        this.result.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load readiness status. Check your permissions and server connection.');
        this.loading.set(false);
      },
    });
  }
}

// ---------------------------------------------------------------------------
// Ledger Mapping Component
// ---------------------------------------------------------------------------
@Component({
  selector: 'app-tally-ledger-mapping',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatTableModule,
    MatSnackBarModule,
  ],
  template: `
    <div class="page-header" style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px;">
      <h2 i18n="@@nav.tally_ledger_mapping">Tally Ledger Mappings</h2>
      <button mat-stroked-button color="primary" (click)="openReadinessCheck()" aria-label="Run Tally readiness check">
        <mat-icon aria-hidden="true">health_and_safety</mat-icon>
        Readiness Check
      </button>
    </div>

    @if (counts()) {
      <div class="counts-row">
        <mat-chip-set aria-label="Voucher queue status filters">
          <mat-chip (click)="setStatusFilter('PENDING_APPROVAL')" [highlighted]="statusFilter() === 'PENDING_APPROVAL'">
            Draft: {{ counts()!.draft }}
          </mat-chip>
          <mat-chip (click)="setStatusFilter('APPROVED')" [highlighted]="statusFilter() === 'APPROVED'">
            Approved: {{ counts()!.approved }}
          </mat-chip>
          <mat-chip (click)="setStatusFilter('SENT')" [highlighted]="statusFilter() === 'SENT'">
            Sent: {{ counts()!.sent }}
          </mat-chip>
          <mat-chip (click)="setStatusFilter('LEDGER_MISSING')" [highlighted]="statusFilter() === 'LEDGER_MISSING'"
            class="chip-warn">
            Ledger Missing: {{ counts()!.ledgerMissing }}
          </mat-chip>
          <mat-chip (click)="setStatusFilter('EXCEPTION')" [highlighted]="statusFilter() === 'EXCEPTION'"
            class="chip-error">
            Failed: {{ counts()!.failed }}
          </mat-chip>
          @if (statusFilter()) {
            <mat-chip (click)="setStatusFilter(null)">Clear filter</mat-chip>
          }
        </mat-chip-set>
      </div>
    }

    <mat-card>
      <mat-card-header><mat-card-title>Add Mapping</mat-card-title></mat-card-header>
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="save()" class="form-row">
          <mat-form-field>
            <mat-label>GL Account Code</mat-label>
            <input matInput formControlName="glAccountCode" placeholder="e.g. 4001" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Tally Ledger Name</mat-label>
            <input matInput formControlName="tallyLedger" placeholder="e.g. Sales Account" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Notes</mat-label>
            <input matInput formControlName="notes" />
          </mat-form-field>
          <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid">Save</button>
        </form>
      </mat-card-content>
    </mat-card>

    <mat-card class="mt-16">
      <mat-card-content>
        <table mat-table [dataSource]="mappings()" class="full-width">
          <ng-container matColumnDef="glAccountCode">
            <th mat-header-cell *matHeaderCellDef>GL Account</th>
            <td mat-cell *matCellDef="let row">{{ row.glAccountCode }}</td>
          </ng-container>
          <ng-container matColumnDef="tallyLedger">
            <th mat-header-cell *matHeaderCellDef>Tally Ledger</th>
            <td mat-cell *matCellDef="let row">{{ row.tallyLedger }}</td>
          </ng-container>
          <ng-container matColumnDef="notes">
            <th mat-header-cell *matHeaderCellDef>Notes</th>
            <td mat-cell *matCellDef="let row">{{ row.notes }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row">
              <button mat-icon-button color="warn" (click)="delete(row.id)" aria-label="Delete mapping">
                <mat-icon aria-hidden="true">delete</mat-icon>
              </button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="cols"></tr>
          <tr mat-row *matRowDef="let row; columns: cols"></tr>
        </table>
      </mat-card-content>
    </mat-card>
  `,
})
export class TallyLedgerMappingComponent implements OnInit {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private snackBar = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  mappings = signal<TallyLedgerMappingDto[]>([]);
  counts = signal<VoucherQueueCountsDto | null>(null);
  statusFilter = signal<string | null>(null);
  cols = ['glAccountCode', 'tallyLedger', 'notes', 'actions'];

  form: FormGroup = this.fb.group({
    glAccountCode: ['', Validators.required],
    tallyLedger: ['', Validators.required],
    notes: [''],
  });

  ngOnInit(): void {
    this.load();
    this.loadCounts();
  }

  load(): void {
    this.http.get<TallyLedgerMappingDto[]>('/api/integration/tally/ledger-mappings').subscribe({
      next: (data) => this.mappings.set(data),
      error: () => this.snackBar.open('Failed to load mappings', 'Close', { duration: 3000 }),
    });
  }

  loadCounts(): void {
    this.http.get<VoucherQueueCountsDto>('/api/integration/tally/voucher-queue/counts').subscribe({
      next: (data) => this.counts.set(data),
      error: () => { /* counts section is optional */ },
    });
  }

  setStatusFilter(status: string | null): void {
    this.statusFilter.set(status);
  }

  save(): void {
    if (this.form.invalid) return;
    this.http.post<TallyLedgerMappingDto>('/api/integration/tally/ledger-mappings', this.form.value).subscribe({
      next: () => {
        this.form.reset();
        this.load();
      },
      error: () => this.snackBar.open('Failed to save mapping', 'Close', { duration: 3000 }),
    });
  }

  delete(id: number): void {
    this.http.delete(`/api/integration/tally/ledger-mappings/${id}`).subscribe({
      next: () => this.load(),
      error: () => this.snackBar.open('Failed to delete mapping', 'Close', { duration: 3000 }),
    });
  }

  openReadinessCheck(): void {
    this.dialog.open(TallyReadinessDialogComponent, { width: '480px', autoFocus: false });
  }
}
