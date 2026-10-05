import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { SessionStore } from '../../core/auth/session.store';
import { GanttScale, barRect, isDrag, movedStart, rowIndexAt, timeToX } from './gantt-geometry';
import { GanttBarDto, GanttDto, SchedulePlanDto, SchedulingApi, UNPLACED_TEXT, WARNING_TEXT } from './scheduling.api';

const ROW_HEIGHT = 48;
const HEADER_HEIGHT = 28;
const SNAP_MINUTES = 15;

interface DragState {
  bar: GanttBarDto;
  originRow: number;
  startMs: number;
  startClientX: number;
  startClientY: number;
  dx: number;
  dy: number;
}

function isoDate(d: Date): string {
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

@Component({
  selector: 'app-schedule-gantt',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Production Schedule</h2>
    </div>

    <div class="filter-bar">
      <button mat-button (click)="shift(-7)" data-test="prev"><mat-icon>chevron_left</mat-icon> Week</button>
      <mat-form-field appearance="outline">
        <mat-label>From</mat-label>
        <input matInput type="date" [formControl]="fromCtrl" (change)="load()" data-test="from" />
      </mat-form-field>
      <button mat-button (click)="shift(7)" data-test="next">Week <mat-icon>chevron_right</mat-icon></button>
      <button mat-button (click)="zoom(-1)" aria-label="Zoom out"><mat-icon>zoom_out</mat-icon></button>
      <button mat-button (click)="zoom(1)" aria-label="Zoom in"><mat-icon>zoom_in</mat-icon></button>
      <ng-container *appHasPermission="'SCHEDULE_MANAGE'">
        <mat-form-field appearance="outline">
          <mat-label>Mode</mat-label>
          <mat-select [formControl]="modeCtrl" data-test="mode">
            <mat-option value="FULL">Replan everything pending</mat-option>
            <mat-option value="ONLY_UNSCHEDULED">Only unscheduled operations</mat-option>
          </mat-select>
        </mat-form-field>
        <button mat-flat-button color="primary" (click)="preview()" data-test="auto-schedule">
          <mat-icon>auto_fix_high</mat-icon> Auto-schedule
        </button>
      </ng-container>
    </div>

    @if (plan(); as p) {
      <section class="preview" data-test="preview">
        <h3 class="mat-h3">Proposed schedule {{ p.applied ? '(applied)' : '(not saved yet)' }}</h3>
        <p data-test="preview-summary">
          {{ p.placements.length }} operation(s) placed, {{ p.unplaced.length }} could not be placed,
          {{ lateOrders(p).length }} order(s) would finish late.
        </p>
        @if (lateOrders(p).length) {
          <p class="warn">Late: {{ lateOrdersText(p) }}</p>
        }
        @for (u of p.unplaced; track u.productionOperationId) {
          <p class="warn">{{ u.orderNumber }}/{{ u.sequenceNo }} {{ u.operationName }}: {{ unplacedText(u.reason) }}</p>
        }
        @for (w of placementWarnings(p); track w) {
          <p class="note">{{ w }}</p>
        }
        <div class="actions">
          @if (!p.applied) {
            <button mat-flat-button color="primary" (click)="apply()" data-test="apply">
              <mat-icon>check</mat-icon> Apply
            </button>
          }
          <button mat-button (click)="plan.set(null)" data-test="dismiss">Dismiss</button>
        </div>
      </section>
    }

    @if (gantt(); as g) {
      <div class="gantt" data-test="gantt">
        <div class="labels" [style.paddingTop.px]="headerHeight">
          @for (m of g.machines; track m.machineId) {
            <div class="label" [style.height.px]="rowHeight" data-test="machine-label">
              <strong>{{ m.machineCode }}</strong>
              <span>{{ m.machineName }}</span>
            </div>
          }
        </div>
        <div class="scroll">
          <div
            class="canvas"
            [style.width.px]="canvasWidth()"
            [style.height.px]="headerHeight + g.machines.length * rowHeight"
          >
            @for (d of g.days; track d.date) {
              <div
                class="day"
                [class.off]="!d.working"
                [style.left.px]="x(d.start)"
                [style.width.px]="x(d.end) - x(d.start)"
                data-test="day"
              >
                <span class="day-label">{{ d.date }}</span>
              </div>
            }
            @for (m of g.machines; track m.machineId; let i = $index) {
              <div class="row-line" [style.top.px]="headerHeight + (i + 1) * rowHeight"></div>
              @for (b of m.maintenance; track b.maintenanceRequestId) {
                <div
                  class="maintenance"
                  [style]="maintenanceStyle(b.date, i)"
                  [title]="'Maintenance ' + b.requestNumber"
                  data-test="maintenance"
                >
                  <mat-icon>build</mat-icon>
                </div>
              }
              @for (b of m.bars; track b.productionOperationId) {
                <div
                  class="bar"
                  [class.pending]="b.status === 'PENDING'"
                  [class.running]="b.status === 'IN_PROGRESS'"
                  [class.done]="b.status === 'COMPLETED'"
                  [class.late]="b.late"
                  [class.dragging]="drag()?.bar?.productionOperationId === b.productionOperationId"
                  [class.selected]="selected()?.productionOperationId === b.productionOperationId"
                  [class.movable]="canMove(b)"
                  [style]="barStyle(b, i)"
                  [title]="barTitle(b)"
                  (pointerdown)="down($event, b, i)"
                  data-test="bar"
                >
                  {{ b.orderNumber }}/{{ b.sequenceNo }} {{ b.operationName }}
                </div>
              }
            }
          </div>
        </div>
      </div>

      @if (selected(); as s) {
        <section class="detail" data-test="detail">
          <h3 class="mat-h3">{{ s.orderNumber }} / {{ s.sequenceNo }} {{ s.operationName }}</h3>
          <p>
            {{ s.productCode }}, priority {{ s.priority }}, {{ s.status }}<br />
            {{ fmt(s.start) }} to {{ fmt(s.end) }}<br />
            Operator: {{ s.operatorName ?? 'not assigned' }}
            @if (s.late) {
              <br /><span class="warn">The order is scheduled to finish after its due date.</span>
            }
          </p>
          <button
            *appHasPermission="'SCHEDULE_MANAGE'"
            mat-button
            [disabled]="s.locked"
            (click)="unschedule(s)"
            data-test="unschedule"
          >
            <mat-icon>event_busy</mat-icon> Unschedule
          </button>
        </section>
      }

      <h3 class="mat-h3">Not yet scheduled ({{ g.unscheduled.length }})</h3>
      <table class="unscheduled" data-test="unscheduled">
        @for (u of g.unscheduled; track u.productionOperationId) {
          <tr>
            <td>{{ u.orderNumber }}/{{ u.sequenceNo }}</td>
            <td>{{ u.operationName }}</td>
            <td>priority {{ u.priority }}</td>
          </tr>
        } @empty {
          <tr>
            <td class="no-data">Everything pending has a window</td>
          </tr>
        }
      </table>
    }
  `,
  styles: `
    .filter-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
      padding: 8px 0;
    }
    .gantt {
      display: flex;
      border: 1px solid rgba(0, 0, 0, 0.12);
      border-radius: 6px;
      overflow: hidden;
    }
    .labels {
      flex: 0 0 150px;
      border-right: 1px solid rgba(0, 0, 0, 0.12);
      background: rgba(0, 0, 0, 0.03);
    }
    .label {
      display: flex;
      flex-direction: column;
      justify-content: center;
      padding: 0 8px;
      border-top: 1px solid rgba(0, 0, 0, 0.08);
      font-size: 0.8rem;
    }
    .scroll {
      flex: 1;
      overflow-x: auto;
    }
    .canvas {
      position: relative;
      user-select: none;
      touch-action: none;
    }
    .day {
      position: absolute;
      top: 0;
      bottom: 0;
      border-left: 1px solid rgba(0, 0, 0, 0.1);
    }
    .day.off {
      background: repeating-linear-gradient(
        45deg,
        rgba(0, 0, 0, 0.05),
        rgba(0, 0, 0, 0.05) 6px,
        transparent 6px,
        transparent 12px
      );
    }
    .day-label {
      display: block;
      padding: 4px;
      font-size: 0.7rem;
      opacity: 0.7;
    }
    .row-line {
      position: absolute;
      left: 0;
      right: 0;
      border-top: 1px solid rgba(0, 0, 0, 0.08);
    }
    .bar {
      position: absolute;
      box-sizing: border-box;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 0.72rem;
      line-height: 1.6;
      color: #fff;
      background: #3f51b5;
      cursor: pointer;
    }
    .bar.running {
      background: #fb8c00;
    }
    .bar.done {
      background: #78909c;
    }
    .bar.late {
      outline: 2px solid #e53935;
    }
    .bar.movable {
      cursor: grab;
    }
    .bar.dragging {
      opacity: 0.75;
      cursor: grabbing;
      z-index: 5;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
    }
    .bar.selected {
      box-shadow: 0 0 0 2px #000;
    }
    .maintenance {
      position: absolute;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(244, 67, 54, 0.18);
      border: 1px dashed #e53935;
      color: #e53935;
      box-sizing: border-box;
    }
    .preview,
    .detail {
      margin: 12px 0;
      padding: 8px 16px;
      border-radius: 6px;
      background: rgba(63, 81, 181, 0.07);
    }
    .warn {
      color: #c62828;
      margin: 2px 0;
    }
    .note {
      margin: 2px 0;
      font-size: 0.85rem;
      opacity: 0.8;
    }
    .unscheduled td {
      padding: 2px 16px 2px 0;
    }
    .no-data {
      color: rgba(0, 0, 0, 0.54);
    }
  `,
})
export class ScheduleGanttComponent implements OnInit {
  private api = inject(SchedulingApi);
  private snack = inject(MatSnackBar);
  private session = inject(SessionStore);

  readonly rowHeight = ROW_HEIGHT;
  readonly headerHeight = HEADER_HEIGHT;

  fromCtrl = new FormControl(isoDate(new Date()), { nonNullable: true });
  modeCtrl = new FormControl('FULL', { nonNullable: true });

  gantt = signal<GanttDto | null>(null);
  plan = signal<SchedulePlanDto | null>(null);
  selected = signal<GanttBarDto | null>(null);
  drag = signal<DragState | null>(null);
  pxPerHour = signal(6);

  readonly scale = computed<GanttScale | null>(() => {
    const g = this.gantt();
    if (!g || g.days.length === 0) return null;
    return { startMs: Date.parse(g.days[0].start), pxPerHour: this.pxPerHour() };
  });

  readonly canvasWidth = computed(() => {
    const g = this.gantt();
    const s = this.scale();
    return g && s ? Math.ceil(timeToX(s, Date.parse(g.days[g.days.length - 1].end))) : 0;
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.gantt(this.fromCtrl.value, null, null).subscribe({
      next: (g) => {
        this.gantt.set(g);
        const keep = this.selected();
        this.selected.set(
          keep
            ? (g.machines.flatMap((m) => m.bars).find((b) => b.productionOperationId === keep.productionOperationId) ??
                null)
            : null,
        );
      },
      error: (e) => this.fail(e, 'Could not load the schedule'),
    });
  }

  shift(days: number): void {
    const d = new Date(`${this.fromCtrl.value}T00:00:00`);
    d.setDate(d.getDate() + days);
    this.fromCtrl.setValue(isoDate(d));
    this.load();
  }

  zoom(direction: number): void {
    this.pxPerHour.update((v) => Math.min(Math.max(v + direction * 2, 2), 24));
  }

  /** Pixels from the left edge of the chart for an ISO instant. */
  x(iso: string): number {
    const s = this.scale();
    return s ? timeToX(s, Date.parse(iso)) : 0;
  }

  canMove(bar: GanttBarDto): boolean {
    return !bar.locked && this.session.hasPermission('SCHEDULE_MANAGE');
  }

  barStyle(bar: GanttBarDto, rowIndex: number): Record<string, string> {
    const s = this.scale();
    if (!s) return {};
    const d = this.drag();
    const moving = d !== null && d.bar.productionOperationId === bar.productionOperationId ? d : null;
    const start = moving ? movedStart(s, moving.startMs, moving.dx, SNAP_MINUTES) : Date.parse(bar.start);
    const end = start + (Date.parse(bar.end) - Date.parse(bar.start));
    const row = moving ? this.targetRow(moving) : rowIndex;
    const r = barRect(s, start, end, row, ROW_HEIGHT, 6);
    return {
      left: `${r.left}px`,
      width: `${r.width}px`,
      top: `${HEADER_HEIGHT + r.top}px`,
      height: `${r.height}px`,
    };
  }

  maintenanceStyle(date: string, rowIndex: number): Record<string, string> {
    const g = this.gantt();
    const day = g?.days.find((d) => d.date === date);
    if (!day) return { display: 'none' };
    return {
      left: `${this.x(day.start)}px`,
      width: `${this.x(day.end) - this.x(day.start)}px`,
      top: `${HEADER_HEIGHT + rowIndex * ROW_HEIGHT}px`,
      height: `${ROW_HEIGHT}px`,
    };
  }

  barTitle(bar: GanttBarDto): string {
    return `${bar.orderNumber}/${bar.sequenceNo} ${bar.operationName}\n${this.fmt(bar.start)} to ${this.fmt(bar.end)}${bar.late ? '\nLate' : ''}`;
  }

  fmt(iso: string): string {
    const zone = this.gantt()?.zone;
    return new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short', timeZone: zone }).format(
      new Date(iso),
    );
  }

  // ── drag to reschedule ──────────────────────────────────────────────────────────────────────────

  down(event: PointerEvent, bar: GanttBarDto, rowIndex: number): void {
    this.selected.set(bar);
    if (!this.canMove(bar)) return;
    (event.target as HTMLElement | null)?.setPointerCapture?.(event.pointerId);
    this.drag.set({
      bar,
      originRow: rowIndex,
      startMs: Date.parse(bar.start),
      startClientX: event.clientX,
      startClientY: event.clientY,
      dx: 0,
      dy: 0,
    });
  }

  @HostListener('document:pointermove', ['$event'])
  move(event: PointerEvent): void {
    const d = this.drag();
    if (!d) return;
    this.drag.set({ ...d, dx: event.clientX - d.startClientX, dy: event.clientY - d.startClientY });
  }

  @HostListener('document:pointerup')
  up(): void {
    const d = this.drag();
    const g = this.gantt();
    const s = this.scale();
    this.drag.set(null);
    if (!d || !g || !s || !isDrag(d.dx, d.dy)) return;
    const machine = g.machines[this.targetRow(d)];
    if (!machine) return;
    this.drop(d.bar, machine.machineId, movedStart(s, d.startMs, d.dx, SNAP_MINUTES));
  }

  /** The row a drag currently points at, from where the bar started. */
  private targetRow(d: DragState): number {
    const count = this.gantt()?.machines.length ?? 0;
    return rowIndexAt(d.originRow * ROW_HEIGHT + ROW_HEIGHT / 2 + d.dy, ROW_HEIGHT, count);
  }

  /** Asks the server to move an operation; it checks every rule, so a refusal is shown as is. */
  drop(bar: GanttBarDto, machineId: number, startMs: number): void {
    this.api.reschedule(bar.productionOperationId, machineId, new Date(startMs).toISOString()).subscribe({
      next: (r) => {
        const warnings = r.warnings.map((w) => WARNING_TEXT[w] ?? w);
        this.snack.open(
          warnings.length ? `Moved, but: ${warnings.join('; ')}` : `Moved to ${r.machineCode}`,
          'Dismiss',
          { duration: warnings.length ? 8000 : 3000 },
        );
        this.load();
      },
      error: (e) => {
        this.fail(e, 'The operation could not be moved');
        this.load();
      },
    });
  }

  unschedule(bar: GanttBarDto): void {
    this.api.unschedule(bar.productionOperationId).subscribe({
      next: () => {
        this.selected.set(null);
        this.load();
      },
      error: (e) => this.fail(e, 'Could not unschedule the operation'),
    });
  }

  // ── auto-schedule ───────────────────────────────────────────────────────────────────────────────

  preview(): void {
    this.api.run(true, null, this.modeCtrl.value).subscribe({
      next: (p) => this.plan.set(p),
      error: (e) => this.fail(e, 'Could not plan the schedule'),
    });
  }

  apply(): void {
    this.api.run(false, null, this.modeCtrl.value).subscribe({
      next: (p) => {
        this.plan.set(p);
        this.snack.open(`${p.placements.length} operation(s) scheduled`, 'Dismiss', { duration: 3000 });
        this.load();
      },
      error: (e) => this.fail(e, 'Could not save the schedule'),
    });
  }

  lateOrders(p: SchedulePlanDto): { orderNumber: string }[] {
    return p.orders.filter((o) => o.late);
  }

  lateOrdersText(p: SchedulePlanDto): string {
    return this.lateOrders(p)
      .map((o) => o.orderNumber)
      .join(', ');
  }

  unplacedText(reason: string): string {
    return UNPLACED_TEXT[reason] ?? reason;
  }

  placementWarnings(p: SchedulePlanDto): string[] {
    return p.placements
      .filter((pl) => pl.warnings.length > 0)
      .map((pl) => `${pl.orderNumber}/${pl.sequenceNo}: ${pl.warnings.map((w) => WARNING_TEXT[w] ?? w).join('; ')}`);
  }

  private fail(err: { error?: { detail?: string } }, fallback: string): void {
    this.snack.open(err?.error?.detail ?? fallback, 'Dismiss', { duration: 6000 });
  }
}
