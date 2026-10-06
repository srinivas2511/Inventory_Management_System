import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { HttpResponse } from '@angular/common/http';
import { SessionStore } from '../../core/auth/session.store';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { saveBlob } from '../documents/documents.api';
import { ImportResult, IntegrationApi } from './integration.api';

/** Bulk master-data import (validate first, then import), full-entity export, and Tally files. */
@Component({
  selector: 'app-data-exchange',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatSnackBarModule,
    MatTableModule,
    MatTabsModule,
    StatusBadgeComponent,
  ],
  template: `
    <h2 class="mat-h2">Data import and export</h2>
    <mat-tab-group>
      @if (canImport) {
        <mat-tab label="Import">
          <div class="pane">
            <p class="hint">
              Upload a .csv or .xlsx file with a header row. Validate with a dry run first: nothing is written until you
              switch it off. Existing codes are reported, never updated.
            </p>
            <div class="row">
              <mat-form-field appearance="outline">
                <mat-label>What to import</mat-label>
                <mat-select [formControl]="importEntity" data-testid="import-entity">
                  @for (e of importEntities(); track e) {
                    <mat-option [value]="e">{{ e }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <button mat-button type="button" (click)="downloadTemplate()" data-testid="template">
                <mat-icon>download</mat-icon> Column template
              </button>
            </div>
            <div class="row">
              <input
                type="file"
                accept=".csv,.xlsx"
                (change)="onFile($event)"
                data-testid="file"
                aria-label="File to import"
              />
              <mat-slide-toggle [checked]="dryRun()" (change)="dryRun.set($event.checked)" data-testid="dry-run">
                Dry run (validate only)
              </mat-slide-toggle>
              <button
                mat-flat-button
                color="primary"
                type="button"
                [disabled]="!file() || busy()"
                (click)="runImport()"
                data-testid="run"
              >
                {{ dryRun() ? 'Validate' : 'Import' }}
              </button>
            </div>

            @if (result(); as r) {
              <div class="summary" data-testid="summary">
                <strong>{{ r.dryRun ? 'Validation' : 'Import' }} of {{ r.entity }}:</strong>
                {{ r.totalRows }} rows,
                @if (r.dryRun) {
                  {{ r.valid }} valid,
                } @else {
                  {{ r.created }} created,
                }
                {{ r.failed }} with errors
                @if (r.rowsTruncated) {
                  <em>(the list below shows errors first and is truncated)</em>
                }
              </div>
              <table mat-table [dataSource]="r.rows" class="mat-elevation-z1">
                <ng-container matColumnDef="row">
                  <th mat-header-cell *matHeaderCellDef>Row</th>
                  <td mat-cell *matCellDef="let x">{{ x.rowNumber }}</td>
                </ng-container>
                <ng-container matColumnDef="key">
                  <th mat-header-cell *matHeaderCellDef>Code</th>
                  <td mat-cell *matCellDef="let x">{{ x.key }}</td>
                </ng-container>
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef>Result</th>
                  <td mat-cell *matCellDef="let x">
                    <app-status-badge
                      [status]="x.status === 'ERROR' ? 'FAILED' : x.status === 'OK' ? 'PASSED' : 'CREATED'"
                    />
                  </td>
                </ng-container>
                <ng-container matColumnDef="message">
                  <th mat-header-cell *matHeaderCellDef>Message</th>
                  <td mat-cell *matCellDef="let x">{{ x.message }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="resultColumns"></tr>
                <tr mat-row *matRowDef="let row; columns: resultColumns" data-testid="result-row"></tr>
              </table>
            }
          </div>
        </mat-tab>
      }

      <mat-tab label="Export">
        <div class="pane">
          <p class="hint">Streams the whole entity (or a date range where it has one). Every export is audited.</p>
          <div class="row">
            <mat-form-field appearance="outline">
              <mat-label>Entity</mat-label>
              <mat-select [formControl]="exportEntity" data-testid="export-entity">
                @for (e of exportEntities(); track e) {
                  <mat-option [value]="e">{{ e }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Format</mat-label>
              <mat-select [formControl]="format">
                <mat-option value="csv">CSV</mat-option>
                <mat-option value="json">JSON</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>From (optional)</mat-label>
              <input matInput type="date" [formControl]="from" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>To (optional)</mat-label>
              <input matInput type="date" [formControl]="to" />
            </mat-form-field>
            <button
              mat-flat-button
              color="primary"
              type="button"
              [disabled]="!exportEntity.value || busy()"
              (click)="runExport()"
              data-testid="export"
            >
              <mat-icon>download</mat-icon> Export
            </button>
          </div>
        </div>
      </mat-tab>

      @if (canTally) {
        <mat-tab label="Tally">
          <div class="pane">
            <p class="hint">
              Chart of accounts and posted journals in Tally import format. Try a small period in a test company first:
              the format has not been verified against a live Tally instance.
            </p>
            <div class="row">
              <button mat-stroked-button type="button" (click)="tallyLedgers()" data-testid="tally-ledgers">
                Ledgers
              </button>
              <mat-form-field appearance="outline">
                <mat-label>From</mat-label>
                <input matInput type="date" [formControl]="tallyFrom" data-testid="tally-from" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>To</mat-label>
                <input matInput type="date" [formControl]="tallyTo" />
              </mat-form-field>
              <button mat-stroked-button type="button" (click)="tallyVouchers()" data-testid="tally-vouchers">
                Vouchers
              </button>
            </div>
          </div>
        </mat-tab>
      }
    </mat-tab-group>
  `,
  styles: `
    .pane {
      padding-top: 16px;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px 16px;
      margin-bottom: 8px;
    }
    .hint {
      color: var(--mat-sys-on-surface-variant, #666);
      max-width: 780px;
    }
    .summary {
      margin: 16px 0 8px;
    }
  `,
})
export class DataExchangeComponent implements OnInit {
  private readonly api = inject(IntegrationApi);
  private readonly snack = inject(MatSnackBar);
  private readonly session = inject(SessionStore);

  readonly canImport = this.session.hasPermission('DATA_IMPORT');
  readonly canTally = this.session.hasPermission('TALLY_EXPORT');
  readonly resultColumns = ['row', 'key', 'status', 'message'];

  readonly importEntities = signal<string[]>([]);
  readonly exportEntities = signal<string[]>([]);
  readonly file = signal<File | null>(null);
  readonly dryRun = signal(true);
  readonly busy = signal(false);
  readonly result = signal<ImportResult | null>(null);

  readonly importEntity = new FormControl<string | null>(null);
  readonly exportEntity = new FormControl<string | null>(null);
  readonly format = new FormControl<'csv' | 'json'>('csv', { nonNullable: true });
  readonly from = new FormControl<string>('', { nonNullable: true });
  readonly to = new FormControl<string>('', { nonNullable: true });
  readonly tallyFrom = new FormControl<string>('', { nonNullable: true });
  readonly tallyTo = new FormControl<string>('', { nonNullable: true });

  ngOnInit(): void {
    if (this.canImport) {
      this.api.importEntities().subscribe({
        next: (e) => {
          this.importEntities.set(e);
          this.importEntity.setValue(e[0] ?? null);
        },
        error: () => this.snack.open('Could not load the importable entities', 'Close', { duration: 4000 }),
      });
    }
    this.api.exportEntities().subscribe({
      next: (e) => this.exportEntities.set(e),
      error: () => this.snack.open('Could not load the exportable entities', 'Close', { duration: 4000 }),
    });
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files && input.files.length > 0 ? input.files[0] : null);
    this.result.set(null);
  }

  runImport(): void {
    const entity = this.importEntity.value;
    const file = this.file();
    if (!entity || !file) return;
    this.busy.set(true);
    this.api.importFile(entity, file, this.dryRun()).subscribe({
      next: (r) => {
        this.result.set(r);
        this.busy.set(false);
      },
      error: (e) => {
        this.busy.set(false);
        this.snack.open(e?.error?.detail ?? 'The file could not be imported', 'Close', { duration: 6000 });
      },
    });
  }

  downloadTemplate(): void {
    const entity = this.importEntity.value;
    if (entity) this.save(this.api.importTemplate(entity), `${entity}-template.csv`);
  }

  runExport(): void {
    const entity = this.exportEntity.value;
    if (!entity) return;
    const format = this.format.value;
    this.busy.set(true);
    this.save(
      this.api.exportEntity(entity, format, this.from.value || null, this.to.value || null),
      `${entity}.${format}`,
    );
  }

  tallyLedgers(): void {
    this.save(this.api.tallyLedgers(), 'tally-ledgers.xml');
  }

  tallyVouchers(): void {
    this.save(this.api.tallyVouchers(this.tallyFrom.value || null, this.tallyTo.value || null), 'tally-vouchers.xml');
  }

  private save(request: ReturnType<IntegrationApi['tallyLedgers']>, fallbackName: string): void {
    request.subscribe({
      next: (response: HttpResponse<Blob>) => {
        this.busy.set(false);
        if (response.body) saveBlob(response.body, fallbackName);
      },
      error: (e) => {
        this.busy.set(false);
        this.snack.open(e?.status === 403 ? 'You are not allowed to do that' : 'The download failed', 'Close', {
          duration: 5000,
        });
      },
    });
  }
}
