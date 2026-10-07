import { Component, inject, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatDividerModule } from '@angular/material/divider';
import { MatListModule } from '@angular/material/list';
import { InsightApi, TraceabilityDto } from './insight.api';

@Component({
  selector: 'app-traceability',
  standalone: true,
  providers: [InsightApi],
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatListModule,
    DatePipe,
    TitleCasePipe,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Traceability</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Batch Type</mat-label>
        <mat-select [formControl]="typeCtrl">
          <mat-option value="MATERIAL">Material Batch</mat-option>
          <mat-option value="FG">FG Batch</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Batch ID</mat-label>
        <input matInput type="number" [formControl]="idCtrl" placeholder="Enter batch ID" />
      </mat-form-field>
      <button mat-raised-button color="primary" [disabled]="!idCtrl.value || loading()" (click)="trace()">
        <mat-icon>search</mat-icon> Trace
      </button>
    </div>

    @if (loading()) {
      <div class="spinner-center"><mat-spinner diameter="40" /></div>
    }

    @if (result(); as r) {
      <div class="trace-result">
        <h3>{{ r.queryType === 'MATERIAL_BATCH' ? 'Material' : 'FG' }} Batch: {{ r.batchNumber }}</h3>
        <mat-list>
          @for (step of r.trail; track $index) {
            <mat-list-item>
              <mat-icon matListItemIcon>{{ stepIcon(step.stepType) }}</mat-icon>
              <span matListItemTitle>{{ step.stepType | titlecase }} — {{ step.reference }}</span>
              <span matListItemLine>{{ step.description }}</span>
              <span matListItemMeta>{{ step.timestamp | date: 'short' }}</span>
            </mat-list-item>
            <mat-divider />
          }
        </mat-list>
        @if (r.trail.length === 0) {
          <p class="empty">No trace steps found for this batch.</p>
        }
      </div>
    }
  `,
  styles: `
    .filter-bar {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
      padding: 8px 0;
      align-items: flex-end;
    }
    .spinner-center {
      display: flex;
      justify-content: center;
      padding: 48px;
    }
    .trace-result {
      margin-top: 16px;
    }
    .empty {
      color: rgba(0, 0, 0, 0.54);
    }
  `,
})
export class TraceabilityComponent {
  private readonly api = inject(InsightApi);

  protected readonly loading = signal(false);
  protected readonly result = signal<TraceabilityDto | null>(null);

  protected readonly typeCtrl = new FormControl<string>('MATERIAL');
  protected readonly idCtrl = new FormControl<number | null>(null);

  protected trace(): void {
    const id = this.idCtrl.value;
    if (!id) return;
    this.loading.set(true);
    this.result.set(null);
    const obs = this.typeCtrl.value === 'FG' ? this.api.traceFgBatch(id) : this.api.traceMaterialBatch(id);
    obs.subscribe({
      next: (r) => {
        this.result.set(r);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected stepIcon(stepType: string): string {
    const icons: Record<string, string> = {
      GRN_RECEIPT: 'local_shipping',
      QUALITY_INSPECTION: 'fact_check',
      MATERIAL_ISSUED: 'output',
      MATERIAL_SOURCE: 'inventory_2',
      PRODUCED: 'precision_manufacturing',
      DISPATCHED: 'send',
    };
    return icons[stepType] ?? 'info';
  }
}
