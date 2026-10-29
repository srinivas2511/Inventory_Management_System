import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

export interface TallyLedgerMappingDto {
  id: number;
  glAccountCode: string;
  tallyLedger: string;
  notes: string;
  createdAt: string;
}

@Component({
  selector: 'app-tally-ledger-mapping',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatTableModule,
    MatSnackBarModule,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.tally_ledger_mapping">Tally Ledger Mappings</h2>
    </div>

    <mat-card>
      <mat-card-header><mat-card-title>Add Mapping</mat-card-title></mat-card-header>
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="save()" class="form-row">
          <mat-form-field>
            <mat-label>GL Account Code</mat-label>
            <input matInput formControlName="glAccountCode" placeholder="e.g. 4001" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Tally Ledger Name</mat-label>
            <input matInput formControlName="tallyLedger" placeholder="e.g. Sales Account" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Notes</mat-label>
            <input matInput formControlName="notes" />
          </mat-form-field>
          <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid">Save</button>
        </form>
      </mat-card-content>
    </mat-card>

    <mat-card class="mt-16">
      <mat-card-content>
        <table mat-table [dataSource]="mappings()" class="full-width">
          <ng-container matColumnDef="glAccountCode">
            <th mat-header-cell *matHeaderCellDef>GL Account</th>
            <td mat-cell *matCellDef="let row">{{ row.glAccountCode }}</td>
          </ng-container>
          <ng-container matColumnDef="tallyLedger">
            <th mat-header-cell *matHeaderCellDef>Tally Ledger</th>
            <td mat-cell *matCellDef="let row">{{ row.tallyLedger }}</td>
          </ng-container>
          <ng-container matColumnDef="notes">
            <th mat-header-cell *matHeaderCellDef>Notes</th>
            <td mat-cell *matCellDef="let row">{{ row.notes }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row">
              <button mat-icon-button color="warn" (click)="delete(row.id)" aria-label="Delete mapping">
                <mat-icon aria-hidden="true">delete</mat-icon>
              </button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="cols"></tr>
          <tr mat-row *matRowDef="let row; columns: cols"></tr>
        </table>
      </mat-card-content>
    </mat-card>
  `,
})
export class TallyLedgerMappingComponent implements OnInit {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private snackBar = inject(MatSnackBar);

  mappings = signal<TallyLedgerMappingDto[]>([]);
  cols = ['glAccountCode', 'tallyLedger', 'notes', 'actions'];

  form: FormGroup = this.fb.group({
    glAccountCode: ['', Validators.required],
    tallyLedger: ['', Validators.required],
    notes: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.http.get<TallyLedgerMappingDto[]>('/api/integration/tally/ledger-mappings').subscribe({
      next: (data) => this.mappings.set(data),
      error: () => this.snackBar.open('Failed to load mappings', 'Close', { duration: 3000 }),
    });
  }

  save(): void {
    if (this.form.invalid) return;
    this.http.post<TallyLedgerMappingDto>('/api/integration/tally/ledger-mappings', this.form.value).subscribe({
      next: () => {
        this.form.reset();
        this.load();
      },
      error: () => this.snackBar.open('Failed to save mapping', 'Close', { duration: 3000 }),
    });
  }

  delete(id: number): void {
    this.http.delete(`/api/integration/tally/ledger-mappings/${id}`).subscribe({
      next: () => this.load(),
      error: () => this.snackBar.open('Failed to delete mapping', 'Close', { duration: 3000 }),
    });
  }
}
