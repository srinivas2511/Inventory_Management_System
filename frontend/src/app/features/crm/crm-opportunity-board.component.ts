import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
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
import { CrmApi, CrmCustomerDto, OPPORTUNITY_STAGES, OpportunityDto, OpportunityStage } from './crm.api';

@Component({
  selector: 'app-crm-opportunity-board',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    IstDatePipe,
    DecimalPipe,
    MatButtonModule,
    MatButtonToggleModule,
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
      <h2 class="mat-h2">Opportunities</h2>
      <mat-button-toggle-group [value]="view()" (change)="view.set($event.value)" aria-label="View">
        <mat-button-toggle value="board">Board</mat-button-toggle>
        <mat-button-toggle value="list">List</mat-button-toggle>
      </mat-button-toggle-group>
    </div>

    <form *appHasPermission="'OPPORTUNITY_MANAGE'" class="edit-form" [formGroup]="form" (ngSubmit)="create()">
      <mat-form-field appearance="outline">
        <mat-label>Customer</mat-label>
        <mat-select formControlName="customerId">
          @for (c of customers(); track c.id) {
            <mat-option [value]="c.id">{{ c.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Description</mat-label>
        <input matInput maxlength="200" formControlName="description" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Estimated value</mat-label>
        <input matInput type="number" min="0" formControlName="estimatedValue" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Expected close</mat-label>
        <input matInput type="date" formControlName="expectedCloseDate" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">
        <mat-icon>add</mat-icon> New opportunity
      </button>
    </form>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else if (view() === 'board') {
      <div class="board">
        @for (stage of stages; track stage) {
          <section class="column">
            <header>
              <app-status-badge [status]="stage" />
              <span class="count">{{ byStage()[stage].length }}</span>
              <span class="total">{{ total(stage) | number: '1.0-0' }}</span>
            </header>
            @for (o of byStage()[stage]; track o.id) {
              <article class="card">
                <div class="number">{{ o.opportunityNumber }}</div>
                <div class="desc">{{ o.description }}</div>
                <div class="meta">{{ o.customerName }}</div>
                <div class="meta">
                  {{ o.estimatedValue | number: '1.0-2' }} · {{ o.probabilityPct }}%
                  @if (o.expectedCloseDate) {
                    · {{ o.expectedCloseDate | istDate: 'dd MMM' }}
                  }
                </div>
                @if (o.lostReason) {
                  <div class="meta">Lost: {{ o.lostReason }}</div>
                }
                <div *appHasPermission="'OPPORTUNITY_MANAGE'" class="moves">
                  @for (next of nextStages(o); track next) {
                    <button mat-stroked-button (click)="move(o, next)">{{ label(next) }}</button>
                  }
                </div>
              </article>
            }
          </section>
        }
      </div>
    } @else {
      <table mat-table [dataSource]="opportunities()" class="mat-elevation-z1">
        <ng-container matColumnDef="number">
          <th mat-header-cell *matHeaderCellDef>Opportunity</th>
          <td mat-cell *matCellDef="let r">{{ r.opportunityNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="description">
          <th mat-header-cell *matHeaderCellDef>Description</th>
          <td mat-cell *matCellDef="let r">{{ r.description }}</td>
        </ng-container>
        <ng-container matColumnDef="customer">
          <th mat-header-cell *matHeaderCellDef>Customer</th>
          <td mat-cell *matCellDef="let r">{{ r.customerName }}</td>
        </ng-container>
        <ng-container matColumnDef="stage">
          <th mat-header-cell *matHeaderCellDef>Stage</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.stage" /></td>
        </ng-container>
        <ng-container matColumnDef="value">
          <th mat-header-cell *matHeaderCellDef>Value</th>
          <td mat-cell *matCellDef="let r">{{ r.estimatedValue | number: '1.0-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="weighted">
          <th mat-header-cell *matHeaderCellDef>Weighted</th>
          <td mat-cell *matCellDef="let r">{{ r.weightedValue | number: '1.0-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="close">
          <th mat-header-cell *matHeaderCellDef>Expected close</th>
          <td mat-cell *matCellDef="let r">{{ r.expectedCloseDate | istDate: 'dd MMM yyyy' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No opportunities
          </td>
        </tr>
      </table>
    }

    @if (lostFor(); as o) {
      <div class="lost-dialog">
        <mat-form-field appearance="outline" class="wide">
          <mat-label>Why was {{ o.opportunityNumber }} lost?</mat-label>
          <input matInput maxlength="200" [formControl]="lostReason" />
        </mat-form-field>
        <button mat-flat-button color="warn" [disabled]="!lostReason.value.trim()" (click)="confirmLost()">
          Close as lost
        </button>
        <button mat-button (click)="lostFor.set(null)">Cancel</button>
      </div>
    }
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: center;
      gap: 16px;
    }
    .edit-form,
    .lost-dialog {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .wide {
      min-width: 280px;
    }
    .board {
      display: grid;
      grid-template-columns: repeat(6, minmax(220px, 1fr));
      gap: 12px;
      overflow-x: auto;
      padding-bottom: 8px;
    }
    .column {
      background: rgba(128, 128, 128, 0.08);
      border-radius: 8px;
      padding: 8px;
      min-height: 160px;
    }
    .column header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
    }
    .count {
      font-weight: 600;
    }
    .total {
      margin-left: auto;
      opacity: 0.7;
      font-size: 12px;
    }
    .card {
      background: var(--mat-sys-surface, #fff);
      border: 1px solid rgba(128, 128, 128, 0.3);
      border-radius: 6px;
      padding: 8px;
      margin-bottom: 8px;
    }
    .number {
      font-size: 12px;
      opacity: 0.7;
    }
    .desc {
      font-weight: 500;
    }
    .meta {
      font-size: 12px;
      opacity: 0.8;
    }
    .moves {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      margin-top: 6px;
    }
  `,
})
export class CrmOpportunityBoardComponent implements OnInit {
  private api = inject(CrmApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  stages = OPPORTUNITY_STAGES;
  columns = ['number', 'description', 'customer', 'stage', 'value', 'weighted', 'close'];
  view = signal<'board' | 'list'>('board');
  loading = signal(false);
  opportunities = signal<OpportunityDto[]>([]);
  customers = signal<CrmCustomerDto[]>([]);
  lostFor = signal<OpportunityDto | null>(null);
  lostReason = this.fb.nonNullable.control('');

  byStage = computed(() => {
    const groups = Object.fromEntries(this.stages.map((s) => [s, [] as OpportunityDto[]])) as Record<
      OpportunityStage,
      OpportunityDto[]
    >;
    for (const o of this.opportunities()) groups[o.stage].push(o);
    return groups;
  });

  form = this.fb.nonNullable.group({
    customerId: this.fb.control<number | null>(null, [Validators.required]),
    description: ['', [Validators.required, Validators.maxLength(200)]],
    estimatedValue: this.fb.control<number | null>(null),
    expectedCloseDate: [''],
  });

  ngOnInit(): void {
    this.load();
    this.api.getCustomers(null, null, 0, 200).subscribe({ next: (p) => this.customers.set(p.content) });
  }

  load(): void {
    this.loading.set(true);
    this.api.getOpportunities(null, null).subscribe({
      next: (page) => {
        this.opportunities.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load opportunities', 'Dismiss', { duration: 3000 });
      },
    });
  }

  total(stage: OpportunityStage): number {
    return this.byStage()[stage].reduce((sum, o) => sum + (o.estimatedValue ?? 0), 0);
  }

  /** Allowed actions mix EDIT with target stage names; only the stages are move buttons. */
  nextStages(o: OpportunityDto): OpportunityStage[] {
    return o.allowedActions.filter((a): a is OpportunityStage => (this.stages as string[]).includes(a));
  }

  label(stage: OpportunityStage): string {
    return stage === 'CLOSED_WON'
      ? 'Won'
      : stage === 'CLOSED_LOST'
        ? 'Lost'
        : stage.charAt(0) + stage.slice(1).toLowerCase();
  }

  create(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.api
      .createOpportunity({
        leadId: null,
        customerId: v.customerId,
        contactId: null,
        description: v.description,
        probabilityPct: null,
        estimatedValue: v.estimatedValue,
        expectedCloseDate: v.expectedCloseDate || null,
        ownerId: null,
        version: null,
      })
      .subscribe({
        next: () => {
          this.form.reset({ customerId: null, description: '', estimatedValue: null, expectedCloseDate: '' });
          this.load();
        },
        error: (err) =>
          this.snack.open(err?.error?.detail ?? 'Could not create opportunity', 'Dismiss', { duration: 5000 }),
      });
  }

  move(o: OpportunityDto, stage: OpportunityStage): void {
    if (stage === 'CLOSED_LOST') {
      this.lostReason.reset('');
      this.lostFor.set(o);
      return;
    }
    this.advance(o, stage, null);
  }

  confirmLost(): void {
    const o = this.lostFor();
    if (!o) return;
    this.advance(o, 'CLOSED_LOST', this.lostReason.value.trim());
    this.lostFor.set(null);
  }

  private advance(o: OpportunityDto, stage: OpportunityStage, lostReason: string | null): void {
    this.api.advanceOpportunity(o.id, stage, lostReason).subscribe({
      next: () => this.load(),
      error: (err) =>
        this.snack.open(err?.error?.detail ?? 'Could not move opportunity', 'Dismiss', { duration: 5000 }),
    });
  }
}
