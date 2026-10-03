import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { CycleCountDto, CycleCountLineDto, WmsApi } from './wms.api';

/**
 * Count entry and variance review. While a count is open it is blind: the server hides the system quantity, so the
 * counter cannot be anchored by it. Review shows system quantity and variance; posting raises stock adjustments that
 * still need their normal approvals.
 */
@Component({
  selector: 'app-wms-cycle-count-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      @if (count(); as c) {
        <div class="page-header">
          <h2 class="mat-h2" data-test="title">
            {{ c.countNumber }} - {{ c.countType }}{{ c.abcClass ? ' ' + c.abcClass : '' }}
          </h2>
          <app-status-badge [status]="c.status" />
        </div>
        <p class="meta">
          {{ c.countedCount }} of {{ c.lineCount }} lines counted.
          @if (c.status === 'REVIEW' || c.status === 'POSTED') {
            {{ c.varianceLineCount }} line(s) differ from the system.
          }
          @if (c.notes) {
            {{ c.notes }}
          }
        </p>

        <div *appHasPermission="'CYCLE_COUNT_MANAGE'" class="actions">
          @if (c.allowedActions.includes('COUNT')) {
            <button mat-flat-button color="primary" (click)="save()" data-test="save">
              <mat-icon>save</mat-icon> Save counts
            </button>
          }
          @if (c.allowedActions.includes('REVIEW')) {
            <button mat-stroked-button (click)="review()" data-test="review">
              <mat-icon>rule</mat-icon> Review variances
            </button>
          }
          @if (c.allowedActions.includes('REOPEN')) {
            <button mat-stroked-button (click)="reopen()"><mat-icon>replay</mat-icon> Recount</button>
          }
          @if (c.allowedActions.includes('POST')) {
            <button mat-flat-button color="primary" (click)="post()" data-test="post">
              <mat-icon>fact_check</mat-icon> Post adjustments
            </button>
          }
          @if (c.allowedActions.includes('CANCEL')) {
            <button mat-stroked-button color="warn" (click)="cancel()"><mat-icon>cancel</mat-icon> Cancel count</button>
          }
        </div>

        <table class="lines" data-test="lines">
          <thead>
            <tr>
              <th>#</th>
              <th>Bin</th>
              <th>Item</th>
              <th>Batch</th>
              <th>Status</th>
              @if (!blind()) {
                <th>System</th>
              }
              <th>Counted</th>
              @if (!blind()) {
                <th>Variance</th>
                <th>Adjustment</th>
              }
            </tr>
          </thead>
          <tbody>
            @for (l of c.lines; track l.id) {
              <tr [class.variance]="!blind() && l.varianceQty !== null && l.varianceQty !== 0">
                <td>{{ l.lineNo }}</td>
                <td>{{ l.binCode ?? 'location ' + l.locationId }}</td>
                <td>{{ l.itemCode }} {{ l.itemName }}</td>
                <td>{{ l.batchNumber }}</td>
                <td><app-status-badge [status]="l.stockStatus" /></td>
                @if (!blind()) {
                  <td data-test="system">{{ l.systemQty | number: '1.0-3' }}</td>
                }
                <td>
                  @if (countable()) {
                    <input
                      class="qty"
                      type="number"
                      min="0"
                      step="0.001"
                      [formControl]="control(l.id)"
                      data-test="counted"
                    />
                  } @else {
                    {{ l.countedQty | number: '1.0-3' }}
                  }
                </td>
                @if (!blind()) {
                  <td data-test="variance">{{ l.varianceQty | number: '1.0-3' }}</td>
                  <td>{{ l.adjustmentId ? 'ADJ #' + l.adjustmentId : '' }}</td>
                }
              </tr>
            }
          </tbody>
        </table>
      }
    }
  `,
  styles: `
    .meta {
      color: var(--mat-sys-on-surface-variant, #555);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      padding: 12px 0;
    }
    .lines {
      width: 100%;
      border-collapse: collapse;
    }
    .lines th,
    .lines td {
      text-align: left;
      padding: 6px 8px;
      border-bottom: 1px solid rgba(0, 0, 0, 0.12);
    }
    .qty {
      width: 100px;
    }
    .variance td {
      background: rgba(244, 67, 54, 0.07);
    }
  `,
})
export class WmsCycleCountDetailComponent implements OnInit {
  private api = inject(WmsApi);
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  count = signal<CycleCountDto | null>(null);
  loading = signal(true);
  form = this.fb.group({ rows: this.fb.array<FormGroup>([]) });
  private controls = new Map<number, FormControl<number | null>>();

  ngOnInit(): void {
    this.api.getCycleCount(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: (c) => {
        this.apply(c);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Could not load the count', 'Dismiss', { duration: 4000 });
      },
    });
  }

  blind = () => {
    const s = this.count()?.status;
    return s === 'CREATED' || s === 'IN_PROGRESS';
  };

  countable = () => {
    const s = this.count()?.status;
    return s === 'CREATED' || s === 'IN_PROGRESS';
  };

  control(lineId: number): FormControl<number | null> {
    return this.controls.get(lineId)!;
  }

  private apply(c: CycleCountDto): void {
    this.count.set(c);
    this.controls.clear();
    const rows = this.form.controls.rows as FormArray<FormGroup>;
    rows.clear();
    for (const l of c.lines) {
      const ctrl = new FormControl<number | null>(l.countedQty);
      this.controls.set(l.id, ctrl);
      rows.push(new FormGroup({ counted: ctrl }));
    }
  }

  private fail(err: { error?: { detail?: string } }, fallback: string): void {
    this.snack.open(err?.error?.detail ?? fallback, 'Dismiss', { duration: 6000 });
  }

  private changedLines(): { lineId: number; countedQty: number }[] {
    const c = this.count();
    if (!c) return [];
    return c.lines
      .filter((l: CycleCountLineDto) => {
        const v = this.controls.get(l.id)?.value;
        return v !== null && v !== undefined && Number(v) !== l.countedQty;
      })
      .map((l) => ({ lineId: l.id, countedQty: Number(this.controls.get(l.id)!.value) }));
  }

  save(): void {
    const c = this.count();
    if (!c) return;
    const lines = this.changedLines();
    if (lines.length === 0) {
      this.snack.open('Nothing new to save', 'Dismiss', { duration: 2500 });
      return;
    }
    this.api.recordCounts(c.id, lines).subscribe({
      next: (r) => {
        this.apply(r);
        this.snack.open('Counts saved', 'Dismiss', { duration: 2500 });
      },
      error: (e) => this.fail(e, 'Could not save the counts'),
    });
  }

  review(): void {
    const c = this.count();
    if (!c) return;
    const lines = this.changedLines();
    const go = () =>
      this.api
        .reviewCycleCount(c.id)
        .subscribe({ next: (r) => this.apply(r), error: (e) => this.fail(e, 'Could not review') });
    if (lines.length > 0) {
      this.api
        .recordCounts(c.id, lines)
        .subscribe({ next: go, error: (e) => this.fail(e, 'Could not save the counts') });
    } else {
      go();
    }
  }

  reopen(): void {
    const c = this.count();
    if (c)
      this.api
        .reopenCycleCount(c.id)
        .subscribe({ next: (r) => this.apply(r), error: (e) => this.fail(e, 'Could not reopen') });
  }

  post(): void {
    const c = this.count();
    if (c)
      this.api
        .postCycleCount(c.id)
        .subscribe({ next: (r) => this.apply(r), error: (e) => this.fail(e, 'Could not post') });
  }

  cancel(): void {
    const c = this.count();
    if (c)
      this.api
        .cancelCycleCount(c.id, 'Cancelled from the count screen')
        .subscribe({ next: (r) => this.apply(r), error: (e) => this.fail(e, 'Could not cancel') });
  }
}
