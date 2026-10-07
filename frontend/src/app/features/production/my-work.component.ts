import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';

export interface MyWorkItemDto {
  id: number;
  operationId: number;
  productionOrderId: number;
  productionOrderNumber: string;
  productId: number;
  sequenceNo: number;
  operationName: string;
  machineId: number;
  status: string;
  inputQty: number;
  goodQty: number;
  rejectedQty: number;
  scrapQty: number;
  finalOperation: boolean;
  startBlockedReason?: string | null;
}

@Component({
  selector: 'app-my-work',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDividerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    DatePipe,
    DecimalPipe,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">My Work</h2>
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="48" /></div>
    } @else if (items().length === 0) {
      <div class="empty-work">
        <mat-icon style="font-size:64px;color:#ccc">assignment_turned_in</mat-icon>
        <p>No operations assigned to you right now.</p>
      </div>
    } @else {
      <div class="work-grid">
        @for (item of items(); track item.id) {
          <mat-card class="work-card" [class.active-card]="item.status === 'IN_PROGRESS'">
            <mat-card-header>
              <mat-card-title>{{ item.productionOrderNumber }}</mat-card-title>
              <mat-card-subtitle>Op {{ item.sequenceNo }} · {{ item.operationName }}</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="wip-stats">
                <div class="stat">
                  <span class="stat-value">{{ item.inputQty | number: '1.0-3' }}</span>
                  <span class="stat-label">Input</span>
                </div>
                <div class="stat">
                  <span class="stat-value good">{{ item.goodQty | number: '1.0-3' }}</span>
                  <span class="stat-label">Good</span>
                </div>
                <div class="stat">
                  <span class="stat-value warn">{{ item.rejectedQty | number: '1.0-3' }}</span>
                  <span class="stat-label">Rejected</span>
                </div>
                <div class="stat">
                  <span class="stat-value">{{ item.scrapQty | number: '1.0-3' }}</span>
                  <span class="stat-label">Scrap</span>
                </div>
              </div>

              <mat-divider style="margin: 12px 0;" />

              @if (item.status === 'IN_PROGRESS' && selectedOp() === item.id) {
                <div class="output-form">
                  <mat-form-field appearance="outline" style="width:100%">
                    <mat-label>Good Qty</mat-label>
                    <input matInput type="number" [formControl]="goodCtrl" />
                  </mat-form-field>
                  <mat-form-field appearance="outline" style="width:100%">
                    <mat-label>Rejected Qty</mat-label>
                    <input matInput type="number" [formControl]="rejCtrl" />
                  </mat-form-field>
                  <mat-form-field appearance="outline" style="width:100%">
                    <mat-label>Scrap Qty</mat-label>
                    <input matInput type="number" [formControl]="scrapCtrl" />
                  </mat-form-field>
                  <div class="form-actions">
                    <button mat-raised-button color="primary" (click)="recordOutput(item.id)">
                      <mat-icon>save</mat-icon> Record Output
                    </button>
                    <button mat-stroked-button (click)="cancelInput()">Cancel</button>
                  </div>
                </div>
              } @else if (confirmingOp() === item.id) {
                <div class="confirm-panel">
                  <p>
                    Recorded {{ recordedQty(item) | number: '1.0-3' }} of {{ item.inputQty | number: '1.0-3' }}
                    pieces on this operation.
                  </p>
                  @if (unrecordedQty(item) > 0) {
                    <p class="warn-text">
                      {{ unrecordedQty(item) | number: '1.0-3' }} pieces have no output recorded and will not be passed
                      on.
                    </p>
                  }
                  @if (item.finalOperation) {
                    <p>
                      This is the last operation. Completing it creates the finished-goods batch and requests the final
                      inspection.
                    </p>
                  }
                  <div class="form-actions">
                    <button mat-raised-button color="primary" (click)="completeOp(item)">
                      <mat-icon>check_circle</mat-icon> Complete operation
                    </button>
                    <button mat-stroked-button (click)="cancelComplete()">Cancel</button>
                  </div>
                </div>
              } @else {
                <app-status-badge [status]="item.status" />
              }
            </mat-card-content>
            <mat-card-actions>
              @if (item.status === 'PENDING') {
                <button
                  mat-raised-button
                  color="primary"
                  [disabled]="!!item.startBlockedReason"
                  (click)="startOp(item.id)"
                >
                  <mat-icon>play_arrow</mat-icon> Start
                </button>
                @if (item.startBlockedReason) {
                  <span class="start-blocked" data-testid="start-blocked-reason">{{ item.startBlockedReason }}</span>
                }
              }
              @if (item.status === 'PAUSED') {
                <button mat-raised-button color="primary" (click)="resumeOp(item.id)">
                  <mat-icon>play_arrow</mat-icon> Resume
                </button>
              }
              @if (item.status === 'IN_PROGRESS' && selectedOp() !== item.id && confirmingOp() !== item.id) {
                <button mat-raised-button color="accent" (click)="openOutput(item.id)">
                  <mat-icon>add_task</mat-icon> Record Output
                </button>
                <button mat-stroked-button (click)="pauseOp(item.id)"><mat-icon>pause</mat-icon> Pause</button>
                <button mat-stroked-button (click)="askComplete(item.id)">
                  <mat-icon>check_circle</mat-icon> Complete
                </button>
              }
            </mat-card-actions>
          </mat-card>
        }
      </div>
    }
  `,
  styles: [
    `
      .start-blocked {
        color: #b3261e;
        font-size: 12px;
        margin-left: 8px;
      }
      .work-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
        gap: 16px;
        padding: 8px 0;
      }
      .work-card {
        transition: box-shadow 0.2s;
      }
      .active-card {
        border-left: 4px solid #1976d2;
      }
      .wip-stats {
        display: flex;
        justify-content: space-around;
        margin: 12px 0;
      }
      .stat {
        display: flex;
        flex-direction: column;
        align-items: center;
      }
      .stat-value {
        font-size: 22px;
        font-weight: 700;
      }
      .stat-value.good {
        color: #388e3c;
      }
      .stat-value.warn {
        color: #f57c00;
      }
      .stat-label {
        font-size: 11px;
        color: #888;
        text-transform: uppercase;
      }
      .output-form {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
      .confirm-panel p {
        margin: 4px 0;
      }
      .warn-text {
        color: #f57c00;
      }
      .form-actions {
        display: flex;
        gap: 8px;
        margin-top: 8px;
      }
      .empty-work {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 80px 0;
        color: #aaa;
      }
    `,
  ],
})
export class MyWorkComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);
  private router = inject(Router);

  items = signal<MyWorkItemDto[]>([]);
  loading = signal(false);
  selectedOp = signal<number | null>(null);
  confirmingOp = signal<number | null>(null);

  goodCtrl = new FormControl<number>(0, [Validators.min(0)]);
  rejCtrl = new FormControl<number>(0, [Validators.min(0)]);
  scrapCtrl = new FormControl<number>(0, [Validators.min(0)]);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.http.get<MyWorkItemDto[]>('/api/production/my-work').subscribe({
      next: (items) => {
        this.items.set(items ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  startOp(opId: number): void {
    this.http.post(`/api/production/operations/${opId}/start`, {}).subscribe({
      next: () => {
        this.snack.open('Started', 'OK', { duration: 2000 });
        this.load();
      },
      error: (e) => this.fail(e),
    });
  }

  pauseOp(opId: number): void {
    this.http.post(`/api/production/operations/${opId}/pause`, {}).subscribe({
      next: () => {
        this.snack.open('Paused', 'OK', { duration: 2000 });
        this.load();
      },
      error: (e) => this.fail(e),
    });
  }

  resumeOp(opId: number): void {
    this.http.post(`/api/production/operations/${opId}/resume`, {}).subscribe({
      next: () => {
        this.snack.open('Resumed', 'OK', { duration: 2000 });
        this.load();
      },
      error: (e) => this.fail(e),
    });
  }

  recordedQty(item: MyWorkItemDto): number {
    return item.goodQty + item.rejectedQty + item.scrapQty;
  }

  unrecordedQty(item: MyWorkItemDto): number {
    return Math.max(0, item.inputQty - this.recordedQty(item));
  }

  askComplete(opId: number): void {
    this.selectedOp.set(null);
    this.confirmingOp.set(opId);
  }

  cancelComplete(): void {
    this.confirmingOp.set(null);
  }

  completeOp(item: MyWorkItemDto): void {
    this.http.post(`/api/production/operations/${item.id}/complete`, {}).subscribe({
      next: () => {
        this.confirmingOp.set(null);
        if (item.finalOperation) {
          this.snack
            .open('Operation completed. Final inspection requested for the finished goods.', 'Open inspections', {
              duration: 10000,
            })
            .onAction()
            .subscribe(() => this.router.navigateByUrl('/quality/inspections'));
        } else {
          this.snack.open('Operation completed', 'OK', { duration: 2000 });
        }
        this.load();
      },
      error: (e) => this.fail(e),
    });
  }

  openOutput(opId: number): void {
    this.confirmingOp.set(null);
    this.selectedOp.set(opId);
    this.goodCtrl.setValue(0);
    this.rejCtrl.setValue(0);
    this.scrapCtrl.setValue(0);
  }

  cancelInput(): void {
    this.selectedOp.set(null);
  }

  private fail(e: { error?: { detail?: string } }): void {
    this.snack.open(e.error?.detail ?? 'The request failed. Try again.', 'OK', { duration: 6000 });
  }

  recordOutput(opId: number): void {
    const body = {
      productionOperationId: opId,
      goodQty: this.goodCtrl.value ?? 0,
      rejectedQty: this.rejCtrl.value ?? 0,
      scrapQty: this.scrapCtrl.value ?? 0,
    };
    this.http.post('/api/production/outputs', body).subscribe({
      next: () => {
        this.snack.open('Output recorded', 'OK', { duration: 2000 });
        this.selectedOp.set(null);
        this.load();
      },
      error: (e) => this.fail(e),
    });
  }
}
