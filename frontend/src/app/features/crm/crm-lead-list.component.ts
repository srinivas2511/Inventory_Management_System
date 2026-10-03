import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe, DatePipe } from '@angular/common';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { CrmApi, LeadDto } from './crm.api';

const STATUSES = ['NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED'];

@Component({
  selector: 'app-crm-lead-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
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
      <h2 class="mat-h2">Leads</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl" (selectionChange)="load()">
          <mat-option [value]="null">All</mat-option>
          @for (s of statuses; track s) {
            <mat-option [value]="s">{{ s }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Search</mat-label>
        <input matInput [formControl]="searchCtrl" (keyup.enter)="load()" />
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>search</mat-icon> Search</button>
    </div>

    <form *appHasPermission="'LEAD_MANAGE'" class="edit-form" [formGroup]="form" (ngSubmit)="create()">
      <mat-form-field appearance="outline">
        <mat-label>Company</mat-label>
        <input matInput maxlength="150" formControlName="companyName" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Source</mat-label>
        <input matInput maxlength="30" formControlName="source" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Estimated value</mat-label>
        <input matInput type="number" min="0" formControlName="estimatedValue" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Expected close</mat-label>
        <input matInput type="date" formControlName="expectedCloseDate" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Notes</mat-label>
        <input matInput formControlName="notes" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">
        <mat-icon>add</mat-icon> New lead
      </button>
    </form>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="leads()" class="mat-elevation-z1">
        <ng-container matColumnDef="leadNumber">
          <th mat-header-cell *matHeaderCellDef>Lead</th>
          <td mat-cell *matCellDef="let r">{{ r.leadNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="companyName">
          <th mat-header-cell *matHeaderCellDef>Company</th>
          <td mat-cell *matCellDef="let r">{{ r.companyName }}</td>
        </ng-container>
        <ng-container matColumnDef="contact">
          <th mat-header-cell *matHeaderCellDef>Contact</th>
          <td mat-cell *matCellDef="let r">{{ r.contactName }}</td>
        </ng-container>
        <ng-container matColumnDef="owner">
          <th mat-header-cell *matHeaderCellDef>Owner</th>
          <td mat-cell *matCellDef="let r">{{ r.ownerName }}</td>
        </ng-container>
        <ng-container matColumnDef="value">
          <th mat-header-cell *matHeaderCellDef>Est. value</th>
          <td mat-cell *matCellDef="let r">{{ r.estimatedValue | number: '1.0-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="close">
          <th mat-header-cell *matHeaderCellDef>Expected close</th>
          <td mat-cell *matCellDef="let r">{{ r.expectedCloseDate | date: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row)"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No leads found
          </td>
        </tr>
      </table>
    }
  `,
  styles: `
    .filter-bar,
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .wide {
      min-width: 260px;
    }
    .clickable {
      cursor: pointer;
    }
  `,
})
export class CrmLeadListComponent implements OnInit {
  private api = inject(CrmApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);
  private router = inject(Router);

  statuses = STATUSES;
  columns = ['leadNumber', 'companyName', 'contact', 'owner', 'value', 'close', 'status'];
  leads = signal<LeadDto[]>([]);
  loading = signal(false);

  statusCtrl = new FormControl<string | null>(null);
  searchCtrl = new FormControl('', { nonNullable: true });
  form = this.fb.nonNullable.group({
    companyName: ['', [Validators.required, Validators.maxLength(150)]],
    source: [''],
    estimatedValue: this.fb.control<number | null>(null),
    expectedCloseDate: [''],
    notes: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getLeads(this.statusCtrl.value, this.searchCtrl.value || null).subscribe({
      next: (page) => {
        this.leads.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load leads', 'Dismiss', { duration: 3000 });
      },
    });
  }

  create(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.api
      .createLead({
        companyName: v.companyName,
        contactId: null,
        source: v.source || null,
        ownerId: null,
        estimatedValue: v.estimatedValue,
        expectedCloseDate: v.expectedCloseDate || null,
        notes: v.notes || null,
        version: null,
      })
      .subscribe({
        next: () => {
          this.form.reset({ companyName: '', source: '', estimatedValue: null, expectedCloseDate: '', notes: '' });
          this.load();
        },
        error: (err) => this.snack.open(err?.error?.detail ?? 'Could not create lead', 'Dismiss', { duration: 5000 }),
      });
  }

  open(lead: LeadDto): void {
    this.router.navigate(['/crm/leads', lead.id]);
  }
}
