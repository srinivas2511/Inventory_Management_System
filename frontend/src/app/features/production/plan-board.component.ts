import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { HttpClient } from '@angular/common/http';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { ProductionOperationDto } from './production-order.model';

@Component({
  selector: 'app-plan-board',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatChipsModule,
    MatTooltipModule,
    IstDatePipe,
  ],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title i18n="@@planBoard.title">Production Plan Board</mat-card-title>
        <mat-card-subtitle i18n="@@planBoard.subtitle">Scheduled and in-progress operations</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        @if (loading) {
          <div class="center"><mat-spinner diameter="40"></mat-spinner></div>
        } @else if (operations.length === 0) {
          <p class="empty" i18n="@@planBoard.empty">No active operations scheduled.</p>
        } @else {
          <table mat-table [dataSource]="operations" class="full-width">
            <ng-container matColumnDef="order">
              <th mat-header-cell *matHeaderCellDef i18n="@@planBoard.col.order">Order</th>
              <td mat-cell *matCellDef="let op">{{ op.productionOrderNumber ?? '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="seq">
              <th mat-header-cell *matHeaderCellDef i18n="@@planBoard.col.seq">Seq</th>
              <td mat-cell *matCellDef="let op">{{ op.sequenceNo }}</td>
            </ng-container>
            <ng-container matColumnDef="operation">
              <th mat-header-cell *matHeaderCellDef i18n="@@planBoard.col.operation">Operation</th>
              <td mat-cell *matCellDef="let op">{{ op.operationName }}</td>
            </ng-container>
            <ng-container matColumnDef="machine">
              <th mat-header-cell *matHeaderCellDef i18n="@@planBoard.col.machine">Machine</th>
              <td mat-cell *matCellDef="let op">{{ op.machineName ?? '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef i18n="@@planBoard.col.status">Status</th>
              <td mat-cell *matCellDef="let op">
                <mat-chip [ngClass]="statusClass(op.status)">{{ op.status }}</mat-chip>
              </td>
            </ng-container>
            <ng-container matColumnDef="plannedStart">
              <th mat-header-cell *matHeaderCellDef i18n="@@planBoard.col.plannedStart">Planned Start</th>
              <td mat-cell *matCellDef="let op">{{ (op.plannedStart | istDate: 'short') ?? '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="plannedEnd">
              <th mat-header-cell *matHeaderCellDef i18n="@@planBoard.col.plannedEnd">Planned End</th>
              <td mat-cell *matCellDef="let op">{{ (op.plannedEnd | istDate: 'short') ?? '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="operator">
              <th mat-header-cell *matHeaderCellDef i18n="@@planBoard.col.operator">Operator</th>
              <td mat-cell *matCellDef="let op">{{ op.assignedOperatorName ?? '—' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [
    `
      .center {
        display: flex;
        justify-content: center;
        padding: 2rem;
      }
      .empty {
        color: var(--mat-sys-on-surface-variant);
        padding: 1rem 0;
      }
      .full-width {
        width: 100%;
      }
      .chip-pending {
        background: #e3f2fd !important;
      }
      .chip-in-progress {
        background: #fff3e0 !important;
      }
      .chip-paused {
        background: #fce4ec !important;
      }
    `,
  ],
})
export class PlanBoardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  operations: ProductionOperationDto[] = [];
  loading = true;
  readonly columns = ['order', 'seq', 'operation', 'machine', 'status', 'plannedStart', 'plannedEnd', 'operator'];

  ngOnInit(): void {
    this.http.get<ProductionOperationDto[]>('/api/production/plan-board').subscribe({
      next: (ops) => {
        this.operations = ops;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  statusClass(status: string): string {
    if (status === 'IN_PROGRESS') return 'chip-in-progress';
    if (status === 'PAUSED') return 'chip-paused';
    return 'chip-pending';
  }
}
