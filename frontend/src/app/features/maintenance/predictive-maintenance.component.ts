import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { problemMessage } from '../../core/models/problem.model';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { MachineRiskRow, MaintenanceApi, PredictionDto, RunPredictionsResponse } from './maintenance.api';

@Component({
  selector: 'app-predictive-maintenance',
  standalone: true,
  imports: [
    DecimalPipe,
    HasPermissionDirective,
    IstDatePipe,
    MatButtonModule,
    MatChipsModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    MatTooltipModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Predictive Maintenance</h2>
      <button
        mat-flat-button
        color="primary"
        *appHasPermission="'PREDICTIVE_MAINT_VIEW'"
        [disabled]="running()"
        (click)="runPredictions()"
      >
        @if (running()) {
          <mat-spinner diameter="18" style="display:inline-block;vertical-align:middle;margin-right:6px"></mat-spinner>
        } @else {
          <mat-icon aria-hidden="true">auto_fix_high</mat-icon>
        }
        Run Analysis
      </button>
    </div>

    @if (lastRun()) {
      <section style="margin-bottom:24px">
        <p class="mat-body-2" style="margin:0">
          Last analysis: {{ lastRun()!.summary.machinesAnalyzed }} machines &bull;
          {{ lastRun()!.summary.predictionsGenerated }} predictions &bull;
          <strong>{{ lastRun()!.summary.highRiskCount }} high risk</strong>
        </p>
      </section>
    }

    <!-- Active Predictions -->
    <section>
      <h3 class="mat-h3">Active Predictions</h3>
      @if (predictions().length === 0) {
        <p class="mat-body-2">No active predictions. Run analysis to evaluate machine health.</p>
      } @else {
        <table mat-table [dataSource]="predictions()" class="mat-elevation-z1">
          <ng-container matColumnDef="machine">
            <th mat-header-cell *matHeaderCellDef>Machine</th>
            <td mat-cell *matCellDef="let p">{{ p.machineId }}</td>
          </ng-container>
          <ng-container matColumnDef="predictedAt">
            <th mat-header-cell *matHeaderCellDef>Predicted Failure</th>
            <td mat-cell *matCellDef="let p">{{ p.predictedFailureAt | istDate: 'dd/MM/yyyy' }}</td>
          </ng-container>
          <ng-container matColumnDef="confidence">
            <th mat-header-cell *matHeaderCellDef style="text-align:right">Confidence</th>
            <td mat-cell *matCellDef="let p" style="text-align:right">{{ p.confidencePct | number: '1.0-0' }}%</td>
          </ng-container>
          <ng-container matColumnDef="mtbf">
            <th mat-header-cell *matHeaderCellDef style="text-align:right">MTBF (h)</th>
            <td mat-cell *matCellDef="let p" style="text-align:right">
              {{ p.mtbfHours !== null ? (p.mtbfHours | number: '1.1-1') : '—' }}
            </td>
          </ng-container>
          <ng-container matColumnDef="failures">
            <th mat-header-cell *matHeaderCellDef style="text-align:right">Failures (90d)</th>
            <td mat-cell *matCellDef="let p" style="text-align:right">{{ p.failureCount }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let p">
              <button
                mat-icon-button
                aria-label="Acknowledge prediction"
                matTooltip="Acknowledge"
                (click)="acknowledge(p)"
              >
                <mat-icon aria-hidden="true">check_circle</mat-icon>
              </button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="predCols"></tr>
          <tr mat-row *matRowDef="let row; columns: predCols"></tr>
        </table>
      }
    </section>

    <!-- Machine Risk Summary from last run -->
    @if (machineRows().length > 0) {
      <section style="margin-top:32px">
        <h3 class="mat-h3">Machine Risk Summary</h3>
        <table mat-table [dataSource]="machineRows()" class="mat-elevation-z1">
          <ng-container matColumnDef="machine">
            <th mat-header-cell *matHeaderCellDef>Machine</th>
            <td mat-cell *matCellDef="let r">{{ r.machineCode }} — {{ r.machineName }}</td>
          </ng-container>
          <ng-container matColumnDef="risk">
            <th mat-header-cell *matHeaderCellDef>Risk</th>
            <td mat-cell *matCellDef="let r">
              <mat-chip [class]="'risk-' + r.riskLevel.toLowerCase()">{{ r.riskLevel }}</mat-chip>
            </td>
          </ng-container>
          <ng-container matColumnDef="failures">
            <th mat-header-cell *matHeaderCellDef style="text-align:right">Failures</th>
            <td mat-cell *matCellDef="let r" style="text-align:right">{{ r.failureCountLast90Days }}</td>
          </ng-container>
          <ng-container matColumnDef="mtbf">
            <th mat-header-cell *matHeaderCellDef style="text-align:right">MTBF (h)</th>
            <td mat-cell *matCellDef="let r" style="text-align:right">{{ r.mtbfHours | number: '1.1-1' }}</td>
          </ng-container>
          <ng-container matColumnDef="next">
            <th mat-header-cell *matHeaderCellDef>Predicted Next Failure</th>
            <td mat-cell *matCellDef="let r">{{ r.nextPredictedFailure | istDate: 'dd/MM/yyyy' }}</td>
          </ng-container>
          <ng-container matColumnDef="confidence">
            <th mat-header-cell *matHeaderCellDef style="text-align:right">Confidence</th>
            <td mat-cell *matCellDef="let r" style="text-align:right">{{ r.confidencePct | number: '1.0-0' }}%</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="riskCols"></tr>
          <tr mat-row *matRowDef="let row; columns: riskCols"></tr>
        </table>
      </section>
    }
  `,
  styles: [
    `
      .page-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 24px;
      }
      .risk-high {
        background: #ffcdd2 !important;
        color: #b71c1c !important;
      }
      .risk-medium {
        background: #fff9c4 !important;
        color: #f57f17 !important;
      }
      .risk-low {
        background: #e8f5e9 !important;
        color: #1b5e20 !important;
      }
    `,
  ],
})
export class PredictiveMaintenanceComponent implements OnInit {
  private api = inject(MaintenanceApi);
  private snackBar = inject(MatSnackBar);

  predCols = ['machine', 'predictedAt', 'confidence', 'mtbf', 'failures', 'actions'];
  riskCols = ['machine', 'risk', 'failures', 'mtbf', 'next', 'confidence'];

  predictions = signal<PredictionDto[]>([]);
  machineRows = signal<MachineRiskRow[]>([]);
  running = signal(false);
  lastRun = signal<RunPredictionsResponse | null>(null);

  ngOnInit(): void {
    this.loadPredictions();
  }

  private loadPredictions(): void {
    this.api.getPredictions(false).subscribe({
      next: (p) => this.predictions.set(p),
      error: (err) =>
        this.snackBar.open(problemMessage(err, 'Failed to load predictions'), 'Close', { duration: 4000 }),
    });
  }

  runPredictions(): void {
    this.running.set(true);
    this.api.runPredictions().subscribe({
      next: (resp) => {
        this.running.set(false);
        this.lastRun.set(resp);
        this.machineRows.set(resp.machines);
        this.loadPredictions();
        this.snackBar.open(
          `Analysis complete — ${resp.summary.predictionsGenerated} predictions, ${resp.summary.highRiskCount} high-risk`,
          'Close',
          { duration: 5000 },
        );
      },
      error: (err) => {
        this.running.set(false);
        this.snackBar.open(problemMessage(err, 'Analysis failed'), 'Close', { duration: 4000 });
      },
    });
  }

  acknowledge(pred: PredictionDto): void {
    this.api.acknowledgePrediction(pred.id).subscribe({
      next: () => {
        this.predictions.update((list) => list.filter((p) => p.id !== pred.id));
        this.snackBar.open('Prediction acknowledged', 'Close', { duration: 3000 });
      },
      error: (err) => this.snackBar.open(problemMessage(err, 'Failed to acknowledge'), 'Close', { duration: 4000 }),
    });
  }
}
