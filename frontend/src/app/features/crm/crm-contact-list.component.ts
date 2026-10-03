import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ContactDto, CrmApi, CrmCustomerDto } from './crm.api';

@Component({
  selector: 'app-crm-contact-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Contacts</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Customer</mat-label>
        <mat-select [formControl]="customerFilter" (selectionChange)="load()">
          <mat-option [value]="null">All</mat-option>
          @for (c of customers(); track c.id) {
            <mat-option [value]="c.id">{{ c.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Search</mat-label>
        <input matInput [formControl]="searchCtrl" (keyup.enter)="load()" />
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>search</mat-icon> Search</button>
    </div>

    <form *appHasPermission="'CUSTOMER_ACTIVITY'" class="edit-form" [formGroup]="form" (ngSubmit)="create()">
      <mat-form-field appearance="outline">
        <mat-label>Customer</mat-label>
        <mat-select formControlName="customerId">
          <mat-option [value]="null">(none yet)</mat-option>
          @for (c of customers(); track c.id) {
            <mat-option [value]="c.id">{{ c.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>First name</mat-label>
        <input matInput maxlength="60" formControlName="firstName" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Last name</mat-label>
        <input matInput maxlength="60" formControlName="lastName" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Email</mat-label>
        <input matInput type="email" formControlName="email" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Phone</mat-label>
        <input matInput maxlength="20" formControlName="phone" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Designation</mat-label>
        <input matInput maxlength="80" formControlName="designation" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">
        <mat-icon>add</mat-icon> Add contact
      </button>
    </form>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="contacts()" class="mat-elevation-z1">
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let r">{{ r.fullName }}</td>
        </ng-container>
        <ng-container matColumnDef="customer">
          <th mat-header-cell *matHeaderCellDef>Customer</th>
          <td mat-cell *matCellDef="let r">{{ r.customerName }}</td>
        </ng-container>
        <ng-container matColumnDef="designation">
          <th mat-header-cell *matHeaderCellDef>Designation</th>
          <td mat-cell *matCellDef="let r">{{ r.designation }}</td>
        </ng-container>
        <ng-container matColumnDef="email">
          <th mat-header-cell *matHeaderCellDef>Email</th>
          <td mat-cell *matCellDef="let r">{{ r.email }}</td>
        </ng-container>
        <ng-container matColumnDef="phone">
          <th mat-header-cell *matHeaderCellDef>Phone</th>
          <td mat-cell *matCellDef="let r">{{ r.phone }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button *appHasPermission="'CUSTOMER_ACTIVITY'" mat-button color="warn" (click)="deactivate(r)">
              Deactivate
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No contacts found
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
  `,
})
export class CrmContactListComponent implements OnInit {
  private api = inject(CrmApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  columns = ['name', 'customer', 'designation', 'email', 'phone', 'actions'];
  contacts = signal<ContactDto[]>([]);
  customers = signal<CrmCustomerDto[]>([]);
  loading = signal(false);

  customerFilter = new FormControl<number | null>(null);
  searchCtrl = new FormControl('', { nonNullable: true });
  form = this.fb.nonNullable.group({
    customerId: this.fb.control<number | null>(null),
    firstName: ['', [Validators.required, Validators.maxLength(60)]],
    lastName: [''],
    email: ['', [Validators.email]],
    phone: [''],
    designation: [''],
  });

  ngOnInit(): void {
    this.api.getCustomers(null, null, 0, 200).subscribe({ next: (p) => this.customers.set(p.content) });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getContacts(this.customerFilter.value, this.searchCtrl.value || null).subscribe({
      next: (page) => {
        this.contacts.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load contacts', 'Dismiss', { duration: 3000 });
      },
    });
  }

  create(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.api
      .createContact({
        customerId: v.customerId,
        firstName: v.firstName,
        lastName: v.lastName || null,
        email: v.email || null,
        phone: v.phone || null,
        designation: v.designation || null,
        department: null,
        version: null,
      })
      .subscribe({
        next: () => {
          this.form.reset({ customerId: null, firstName: '', lastName: '', email: '', phone: '', designation: '' });
          this.load();
        },
        error: (err) => this.snack.open(err?.error?.detail ?? 'Could not save contact', 'Dismiss', { duration: 5000 }),
      });
  }

  deactivate(c: ContactDto): void {
    this.api.deactivateContact(c.id).subscribe({
      next: () => this.load(),
      error: () => this.snack.open('Could not deactivate contact', 'Dismiss', { duration: 3000 }),
    });
  }
}
