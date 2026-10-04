import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { QualityPlusApi, SpcCapability, SpcChartDetail, SpcPoint } from './quality-plus.api';
import { SpcChartSvgComponent } from './spc-chart-svg.component';
import { ChartPointInput, parseValues } from './spc-chart.util';

@Component({
  selector: 'app-spc-chart-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    HasPermissionDirective,
    SpcChartSvgComponent,
  ],
  template: `
    <div class="page-header">
      <div>
        <a routerLink="/quality/spc" class="back"><mat-icon>arrow_back</mat-icon> Control charts</a>
        @if (chart(); as c) {
          <h2 class="mat-h2">
            {{ c.summary.productCode }} {{ c.summary.productName }} - {{ c.summary.parameterName }}
            <span class="type">{{ c.summary.chartType }}</span>
          </h2>
        }
      </div>
      @if (chart()) {
        <button *appHasPermission="'SPC_MANAGE'" mat-stroked-button (click)="recalculate()" [disabled]="busy()">
          <mat-icon>calculate</mat-icon> Recalculate limits
        </button>
      }
    </div>

    @if (chart(); as c) {
      <p class="limits-note">
        @if (c.limitsEstablishedAt) {
          Control limits established {{ c.limitsEstablishedAt | date: 'dd MMM yyyy HH:mm' }}.
        } @else {
          Control limits are not established yet; they are set automatically once enough points are collected.
        }
        @if (c.summary.outOfControlCount > 0) {
          <strong class="ooc"> {{ c.summary.outOfControlCount }} out-of-control point(s).</strong>
        }
      </p>

      @if (c.summary.chartType === 'XBAR_R') {
        <app-spc-chart-svg
          title="X-bar chart (subgroup means)"
          [points]="meanPoints()"
          [cl]="c.cl"
          [ucl]="c.ucl"
          [lcl]="c.lcl"
        />
        <app-spc-chart-svg
          title="R chart (subgroup ranges)"
          [points]="rangePoints()"
          [cl]="c.clR"
          [ucl]="c.uclR"
          [lcl]="c.lclR"
        />
      } @else {
        <app-spc-chart-svg
          [title]="c.summary.chartType === 'P' ? 'P chart (proportion defective)' : 'C chart (defects per unit)'"
          [points]="attributePoints()"
          [cl]="c.cl"
          [ucl]="c.ucl"
          [lcl]="c.lcl"
        />
      }

      <section *appHasPermission="'QUALITY_INSPECT'" class="panel">
        <h3 class="mat-h3">Add data</h3>
        <form [formGroup]="entry" (ngSubmit)="addPoints()" class="row">
          @if (c.summary.chartType === 'XBAR_R') {
            <mat-form-field appearance="outline" class="wide">
              <mat-label>Measured values (subgroup of {{ c.summary.subgroupSize }})</mat-label>
              <input matInput formControlName="values" placeholder="10.1 10.2 9.9" />
            </mat-form-field>
          } @else {
            @if (c.summary.chartType === 'P') {
              <mat-form-field appearance="outline">
                <mat-label>Units inspected</mat-label>
                <input matInput type="number" formControlName="sampleSize" min="1" />
              </mat-form-field>
            }
            <mat-form-field appearance="outline">
              <mat-label>{{ c.summary.chartType === 'P' ? 'Defective units' : 'Defects' }}</mat-label>
              <input matInput type="number" formControlName="defectCount" min="0" />
            </mat-form-field>
          }
          <button mat-flat-button color="primary" type="submit" [disabled]="busy()">Add</button>
        </form>
      </section>

      @if (c.summary.chartType === 'XBAR_R') {
        <section class="panel">
          <h3 class="mat-h3">Specification and capability</h3>
          <form [formGroup]="spec" (ngSubmit)="saveSpec()" class="row">
            <mat-form-field appearance="outline">
              <mat-label>Upper spec limit</mat-label>
              <input matInput type="number" formControlName="usl" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Lower spec limit</mat-label>
              <input matInput type="number" formControlName="lsl" />
            </mat-form-field>
            <button *appHasPermission="'SPC_MANAGE'" mat-stroked-button type="submit" [disabled]="busy()">
              Save limits
            </button>
          </form>
          <form [formGroup]="period" (ngSubmit)="calculate()" class="row">
            <mat-form-field appearance="outline">
              <mat-label>From</mat-label>
              <input matInput type="date" formControlName="from" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>To</mat-label>
              <input matInput type="date" formControlName="to" />
            </mat-form-field>
            <button mat-stroked-button type="submit" [disabled]="busy()">Calculate Cp / Cpk</button>
          </form>
          @if (capability(); as k) {
            <dl class="capability">
              <dt>Cp</dt>
              <dd>{{ k.cp === null ? '-' : (k.cp | number: '1.2-2') }}</dd>
              <dt>Cpk</dt>
              <dd>{{ k.cpk === null ? '-' : (k.cpk | number: '1.2-2') }}</dd>
              <dt>Cpu / Cpl</dt>
              <dd>
                {{ k.cpu === null ? '-' : (k.cpu | number: '1.2-2') }} /
                {{ k.cpl === null ? '-' : (k.cpl | number: '1.2-2') }}
              </dd>
              <dt>Mean</dt>
              <dd>{{ k.mean | number: '1.3-3' }}</dd>
              <dt>Std. deviation</dt>
              <dd>{{ k.stdDev | number: '1.4-4' }}</dd>
              <dt>Measurements</dt>
              <dd>{{ k.count }}</dd>
            </dl>
          }
        </section>
      }

      @if (flagged().length > 0) {
        <section class="panel">
          <h3 class="mat-h3">Out-of-control points</h3>
          <table mat-table [dataSource]="flagged()" class="mat-elevation-z1">
            <ng-container matColumnDef="subgroup">
              <th mat-header-cell *matHeaderCellDef>Subgroup</th>
              <td mat-cell *matCellDef="let p">{{ p.subgroupNo }}</td>
            </ng-container>
            <ng-container matColumnDef="value">
              <th mat-header-cell *matHeaderCellDef>Value</th>
              <td mat-cell *matCellDef="let p">{{ plotValue(p) | number: '1.3-3' }}</td>
            </ng-container>
            <ng-container matColumnDef="rule">
              <th mat-header-cell *matHeaderCellDef>Rule</th>
              <td mat-cell *matCellDef="let p">{{ p.violationRule }}</td>
            </ng-container>
            <ng-container matColumnDef="at">
              <th mat-header-cell *matHeaderCellDef>When</th>
              <td mat-cell *matCellDef="let p">{{ p.measuredAt | date: 'dd MMM yyyy HH:mm' }}</td>
            </ng-container>
            <ng-container matColumnDef="action">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let p">
                <button *appHasPermission="'CAPA_MANAGE'" mat-button (click)="raiseCapa(p)" [disabled]="busy()">
                  Raise CAPA
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="flaggedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: flaggedColumns"></tr>
          </table>
        </section>
      }
    } @else {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    }
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
    }
    .back {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      text-decoration: none;
    }
    .type {
      font-size: 0.7em;
      font-weight: 400;
      margin-left: 8px;
      opacity: 0.7;
    }
    .limits-note {
      margin: 0 0 12px;
    }
    .ooc {
      color: #c62828;
    }
    .panel {
      margin: 16px 0;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
    }
    .wide {
      min-width: 320px;
    }
    .capability {
      display: grid;
      grid-template-columns: max-content max-content;
      gap: 4px 16px;
    }
    .capability dt {
      font-weight: 500;
    }
    .capability dd {
      margin: 0;
    }
  `,
})
export class SpcChartDetailComponent implements OnInit {
  private api = inject(QualityPlusApi);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private snack = inject(MatSnackBar);

  chart = signal<SpcChartDetail | null>(null);
  capability = signal<SpcCapability | null>(null);
  busy = signal(false);
  flaggedColumns = ['subgroup', 'value', 'rule', 'at', 'action'];

  entry = new FormGroup({
    values: new FormControl('', { nonNullable: true }),
    sampleSize: new FormControl<number | null>(null),
    defectCount: new FormControl<number | null>(null, Validators.min(0)),
  });
  spec = new FormGroup({
    usl: new FormControl<number | null>(null),
    lsl: new FormControl<number | null>(null),
  });
  period = new FormGroup({
    from: new FormControl('', { nonNullable: true }),
    to: new FormControl('', { nonNullable: true }),
  });

  /** Subgroup means (X-bar/R): the last measurement of every completed subgroup. */
  meanPoints = computed<ChartPointInput[]>(() => {
    const c = this.chart();
    if (!c) return [];
    return this.completed(c).map((p) => ({
      value: p.subgroupMean as number,
      outOfControl: p.outOfControl && (p.violationRule ?? '').includes('NELSON'),
      label: `Subgroup ${p.subgroupNo}: mean ${p.subgroupMean}`,
    }));
  });

  rangePoints = computed<ChartPointInput[]>(() => {
    const c = this.chart();
    if (!c) return [];
    return this.completed(c).map((p) => ({
      value: p.subgroupRange as number,
      outOfControl: p.outOfControl && (p.violationRule ?? '').includes('RANGE'),
      label: `Subgroup ${p.subgroupNo}: range ${p.subgroupRange}`,
    }));
  });

  attributePoints = computed<ChartPointInput[]>(() => {
    const c = this.chart();
    if (!c) return [];
    return c.points.map((p) => ({
      value: p.measuredValue,
      outOfControl: p.outOfControl,
      label: `Point ${p.subgroupNo}: ${p.measuredValue}`,
      ucl: p.pointUcl,
      lcl: p.pointLcl,
    }));
  });

  flagged = computed<SpcPoint[]>(() => (this.chart()?.points ?? []).filter((p) => p.outOfControl));

  ngOnInit(): void {
    this.load();
  }

  plotValue(p: SpcPoint): number {
    return this.chart()?.summary.chartType === 'XBAR_R' ? (p.subgroupMean ?? p.measuredValue) : p.measuredValue;
  }

  private completed(c: SpcChartDetail): SpcPoint[] {
    return c.points.filter((p) => p.measurementNo === c.summary.subgroupSize && p.subgroupMean !== null);
  }

  private id(): number {
    return Number(this.route.snapshot.paramMap.get('id'));
  }

  private show(c: SpcChartDetail): void {
    this.chart.set(c);
    this.spec.patchValue({ usl: c.usl, lsl: c.lsl }, { emitEvent: false });
  }

  load(): void {
    this.api.getChart(this.id()).subscribe({
      next: (c) => this.show(c),
      error: (err) => this.fail(err, 'Failed to load the control chart'),
    });
  }

  addPoints(): void {
    const c = this.chart();
    if (!c) return;
    const v = this.entry.getRawValue();
    let req;
    if (c.summary.chartType === 'XBAR_R') {
      const values = parseValues(v.values);
      if (!values) {
        this.snack.open('Enter measured values separated by spaces or commas', 'Dismiss', { duration: 4000 });
        return;
      }
      req = { values };
    } else if (c.summary.chartType === 'P') {
      if (!v.sampleSize || v.defectCount === null) {
        this.snack.open('Enter the units inspected and the defective count', 'Dismiss', { duration: 4000 });
        return;
      }
      req = { sampleSize: v.sampleSize, defectCount: v.defectCount };
    } else {
      if (v.defectCount === null) {
        this.snack.open('Enter the defect count', 'Dismiss', { duration: 4000 });
        return;
      }
      req = { defectCount: v.defectCount };
    }
    this.busy.set(true);
    this.api.addPoints(c.summary.id, req).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.entry.reset({ values: '', sampleSize: null, defectCount: null });
        this.show(updated);
      },
      error: (err) => this.fail(err, 'Failed to add the data'),
    });
  }

  recalculate(): void {
    const c = this.chart();
    if (!c) return;
    this.busy.set(true);
    this.api.recalculateLimits(c.summary.id).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.show(updated);
        this.snack.open('Control limits recalculated', 'Dismiss', { duration: 3000 });
      },
      error: (err) => this.fail(err, 'Failed to recalculate the limits'),
    });
  }

  saveSpec(): void {
    const c = this.chart();
    if (!c) return;
    const v = this.spec.getRawValue();
    this.busy.set(true);
    this.api.updateChart(c.summary.id, { usl: v.usl, lsl: v.lsl, active: null }).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.show(updated);
        this.snack.open('Specification limits saved', 'Dismiss', { duration: 3000 });
      },
      error: (err) => this.fail(err, 'Failed to save the specification limits'),
    });
  }

  calculate(): void {
    const c = this.chart();
    if (!c) return;
    const v = this.period.getRawValue();
    this.busy.set(true);
    this.api.capability(c.summary.id, v.from || null, v.to || null).subscribe({
      next: (k) => {
        this.busy.set(false);
        this.capability.set(k);
      },
      error: (err) => {
        this.capability.set(null);
        this.fail(err, 'Capability could not be calculated');
      },
    });
  }

  raiseCapa(point: SpcPoint): void {
    this.busy.set(true);
    this.api.capaFromSpcPoint(point.id).subscribe({
      next: (capa) => {
        this.busy.set(false);
        this.router.navigate(['/quality/capa', capa.id]);
      },
      error: (err) => this.fail(err, 'Failed to raise the CAPA'),
    });
  }

  private fail(err: { error?: { detail?: string } }, fallback: string): void {
    this.busy.set(false);
    this.snack.open(err?.error?.detail ?? fallback, 'Dismiss', { duration: 5000 });
  }
}
