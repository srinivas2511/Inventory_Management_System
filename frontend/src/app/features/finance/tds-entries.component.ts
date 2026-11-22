import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';

export interface TdsEntryDto {
  id?: number;
  direction: string;
  partyType: string;
  partyId: number;
  sectionId: number;
  tdsSection?: string;
  tdsSectionDescription?: string;
  baseAmount: number;
  rate: number;
  tdsAmount: number;
  deductionDate: string;
  challanNo?: string;
  fiscalYear?: string;
  createdAt?: string;
}

export interface TdsSummaryDto {
  tdsSection: string;
  description?: string;
  entryCount: number;
  totalBaseAmount: number;
  totalTdsAmount: number;
}

export interface TdsSection {
  id: number;
  section: string;
  description?: string;
  rate: number;
}

export interface PartyOption {
  id: number;
  code: string;
  name: string;
}

@Component({
  selector: 'app-tds-entries',
  standalone: true,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    IstDatePipe,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.item-tds-entries">TDS Entries</h2>
      <button mat-fab extended (click)="showForm.set(true)" aria-label="Record TDS deduction">
        <mat-icon aria-hidden="true">add</mat-icon>
        Record TDS
      </button>
    </div>

    <!-- Summary chips -->
    @if (summary().length > 0) {
      <div class="tds-summary-row" style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:16px;">
        @for (s of summary(); track s.tdsSection) {
          <mat-chip-set>
            <mat-chip>
              {{ s.tdsSection }} — Base: ₹{{ s.totalBaseAmount | number: '1.0-0' }} &nbsp;|&nbsp; TDS: ₹{{
                s.totalTdsAmount | number: '1.0-0'
              }}
              ({{ s.entryCount }})
            </mat-chip>
          </mat-chip-set>
        }
      </div>
    }

    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="entries()" class="mat-elevation-z2 full-width">
        <ng-container matColumnDef="tdsSection">
          <th mat-header-cell *matHeaderCellDef>TDS Section</th>
          <td mat-cell *matCellDef="let row">{{ row.tdsSection ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="partyType">
          <th mat-header-cell *matHeaderCellDef>Party</th>
          <td mat-cell *matCellDef="let row">{{ row.partyType }} #{{ row.partyId }}</td>
        </ng-container>
        <ng-container matColumnDef="baseAmount">
          <th mat-header-cell *matHeaderCellDef>Base Amount</th>
          <td mat-cell *matCellDef="let row">₹{{ row.baseAmount | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="rate">
          <th mat-header-cell *matHeaderCellDef>Rate %</th>
          <td mat-cell *matCellDef="let row">{{ row.rate }}%</td>
        </ng-container>
        <ng-container matColumnDef="tdsAmount">
          <th mat-header-cell *matHeaderCellDef>TDS Amount</th>
          <td mat-cell *matCellDef="let row">₹{{ row.tdsAmount | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="challanNo">
          <th mat-header-cell *matHeaderCellDef>Challan No</th>
          <td mat-cell *matCellDef="let row">{{ row.challanNo ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="deductionDate">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let row">{{ row.deductionDate | istDate }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (entries().length === 0) {
        <p class="empty-state">No TDS entries recorded yet.</p>
      }
    }

    @if (showForm()) {
      <div class="inline-form mat-elevation-z2" style="padding:16px;margin-top:16px;">
        <h3>Record TDS Entry</h3>
        <form [formGroup]="form" class="dialog-form">
          <mat-form-field>
            <mat-label>Direction</mat-label>
            <mat-select formControlName="direction" required>
              <mat-option value="DEDUCTED">Deducted (TDS)</mat-option>
              <mat-option value="COLLECTED">Collected (TCS)</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Party Type</mat-label>
            <mat-select formControlName="partyType" required (selectionChange)="onPartyTypeChange()">
              <mat-option value="SUPPLIER">Supplier</mat-option>
              <mat-option value="CUSTOMER">Customer</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Party</mat-label>
            <mat-select formControlName="partyId" required>
              @if (partiesLoading()) {
                <mat-option disabled>Loading…</mat-option>
              }
              @for (p of parties(); track p.id) {
                <mat-option [value]="p.id">{{ p.code }} · {{ p.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>TDS Section</mat-label>
            <mat-select formControlName="sectionId" required (selectionChange)="onSectionChange($event.value)">
              @for (s of sections(); track s.id) {
                <mat-option [value]="s.id">{{ s.section }} – {{ s.description }} ({{ s.rate }}%)</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Base Amount (₹)</mat-label>
            <input matInput type="number" formControlName="baseAmount" required min="0.01" (input)="recalcTds()" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Rate (%)</mat-label>
            <input matInput type="number" formControlName="rate" required min="0" (input)="recalcTds()" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>TDS Amount (₹)</mat-label>
            <input matInput type="number" formControlName="tdsAmount" required min="0" />
            <mat-hint>Auto-calculated; adjust if needed</mat-hint>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Deduction Date</mat-label>
            <input matInput type="date" formControlName="deductionDate" required />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Challan No</mat-label>
            <input matInput formControlName="challanNo" placeholder="e.g. CHL-2026-001" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Fiscal Year</mat-label>
            <input matInput formControlName="fiscalYear" placeholder="e.g. 2026-27" />
          </mat-form-field>
          <div class="form-actions" style="display:flex;gap:8px;justify-content:flex-end;">
            <button mat-button type="button" (click)="cancelForm()">Cancel</button>
            <button
              mat-flat-button
              color="primary"
              type="button"
              (click)="saveEntry()"
              [disabled]="form.invalid || saving()"
            >
              Save
            </button>
          </div>
        </form>
      </div>
    }
  `,
})
export class TdsEntriesComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);
  private fb = inject(FormBuilder);
  private subs: Subscription[] = [];

  columns = ['tdsSection', 'partyType', 'baseAmount', 'rate', 'tdsAmount', 'challanNo', 'deductionDate'];
  entries = signal<TdsEntryDto[]>([]);
  summary = signal<TdsSummaryDto[]>([]);
  sections = signal<TdsSection[]>([]);
  parties = signal<PartyOption[]>([]);
  partiesLoading = signal(false);
  loading = signal(true);
  saving = signal(false);
  showForm = signal(false);
  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      direction: ['DEDUCTED', Validators.required],
      partyType: ['SUPPLIER', Validators.required],
      partyId: [null, Validators.required],
      sectionId: [null, Validators.required],
      baseAmount: [null, Validators.required],
      rate: [null, Validators.required],
      tdsAmount: [null, Validators.required],
      deductionDate: ['', Validators.required],
      challanNo: [''],
      fiscalYear: [this.currentFiscalYear()],
    });
    this.loadEntries();
    this.loadSummary();
    this.loadSections();
    this.loadParties('SUPPLIER');
  }

  ngOnDestroy(): void {
    this.subs.forEach((s) => s.unsubscribe());
  }

  onPartyTypeChange(): void {
    this.form.patchValue({ partyId: null });
    this.loadParties(this.form.value.partyType);
  }

  onSectionChange(sectionId: number): void {
    const sec = this.sections().find((s) => s.id === sectionId);
    if (sec) {
      this.form.patchValue({ rate: sec.rate });
      this.recalcTds();
    }
  }

  recalcTds(): void {
    const base = Number(this.form.value.baseAmount);
    const rate = Number(this.form.value.rate);
    if (base > 0 && rate >= 0) {
      const tds = Math.round(base * rate) / 100;
      this.form.patchValue({ tdsAmount: tds }, { emitEvent: false });
    }
  }

  cancelForm(): void {
    this.form.reset({
      direction: 'DEDUCTED',
      partyType: 'SUPPLIER',
      partyId: null,
      sectionId: null,
      baseAmount: null,
      rate: null,
      tdsAmount: null,
      deductionDate: '',
      challanNo: '',
      fiscalYear: this.currentFiscalYear(),
    });
    this.loadParties('SUPPLIER');
    this.showForm.set(false);
  }

  private currentFiscalYear(): string {
    const today = new Date();
    const year = today.getMonth() >= 3 ? today.getFullYear() : today.getFullYear() - 1;
    return `${year}-${(year + 1).toString().slice(2)}`;
  }

  private loadSections(): void {
    this.http.get<TdsSection[]>('/api/finance/tds-entries/sections').subscribe({
      next: (data) => this.sections.set(data),
      error: () => this.snackBar.open('Failed to load TDS sections', 'Close', { duration: 3000 }),
    });
  }

  private loadParties(partyType: string): void {
    this.partiesLoading.set(true);
    const url = partyType === 'SUPPLIER' ? '/api/lookups/suppliers' : '/api/lookups/customers';
    this.http.get<{ id: number; code: string; name: string }[]>(url).subscribe({
      next: (data) => {
        this.parties.set(data);
        this.partiesLoading.set(false);
      },
      error: () => {
        this.partiesLoading.set(false);
        this.snackBar.open('Failed to load parties', 'Close', { duration: 3000 });
      },
    });
  }

  private loadEntries(): void {
    this.http.get<TdsEntryDto[]>('/api/finance/tds-entries').subscribe({
      next: (data) => {
        this.entries.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load TDS entries', 'Close', { duration: 3000 });
      },
    });
  }

  private loadSummary(): void {
    this.http.get<TdsSummaryDto[]>('/api/finance/tds-entries/summary').subscribe({
      next: (data) => this.summary.set(data),
      error: () => this.snackBar.open('Failed to load TDS summary', 'Close', { duration: 3000 }),
    });
  }

  saveEntry(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    this.http.post<TdsEntryDto>('/api/finance/tds-entries', this.form.value).subscribe({
      next: (created) => {
        this.entries.update((list) => [created, ...list]);
        this.saving.set(false);
        this.showForm.set(false);
        this.loadSummary();
        this.snackBar.open('TDS entry recorded', 'OK', { duration: 2500 });
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open('Failed to record TDS entry', 'Close', { duration: 3000 });
      },
    });
  }
}
