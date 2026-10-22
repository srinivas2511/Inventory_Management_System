import { Component, OnInit, inject, signal } from '@angular/core';

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
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { EMPLOYMENT_TYPES, EmployeeDto, EmploymentType, HrmApi } from './hrm.api';

@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    IstDatePipe,
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
      <h2 class="mat-h2">Employees</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Search</mat-label>
        <input matInput [formControl]="searchCtrl" (keyup.enter)="load()" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="activeCtrl" (selectionChange)="load()">
          <mat-option [value]="null">All</mat-option>
          <mat-option [value]="true">Active</mat-option>
          <mat-option [value]="false">Inactive</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>search</mat-icon> Search</button>
    </div>

    <form *appHasPermission="'EMPLOYEE_MANAGE'" class="edit-form" [formGroup]="form" (ngSubmit)="create()">
      <mat-form-field appearance="outline">
        <mat-label>First name</mat-label>
        <input matInput maxlength="60" formControlName="firstName" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Last name</mat-label>
        <input matInput maxlength="60" formControlName="lastName" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Designation</mat-label>
        <input matInput maxlength="80" formControlName="designation" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Department</mat-label>
        <input matInput maxlength="60" formControlName="department" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Joining date</mat-label>
        <input matInput type="date" formControlName="dateOfJoining" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Type</mat-label>
        <mat-select formControlName="employmentType">
          @for (t of employmentTypes; track t) {
            <mat-option [value]="t">{{ t }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Basic pay</mat-label>
        <input matInput type="number" min="0" formControlName="basicPay" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">
        <mat-icon>person_add</mat-icon> New employee
      </button>
    </form>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="employees()" class="mat-elevation-z1">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let r">{{ r.employeeCode }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let r">{{ r.fullName }}</td>
        </ng-container>
        <ng-container matColumnDef="designation">
          <th mat-header-cell *matHeaderCellDef>Designation</th>
          <td mat-cell *matCellDef="let r">{{ r.designation }}</td>
        </ng-container>
        <ng-container matColumnDef="department">
          <th mat-header-cell *matHeaderCellDef>Department</th>
          <td mat-cell *matCellDef="let r">{{ r.department }}</td>
        </ng-container>
        <ng-container matColumnDef="joined">
          <th mat-header-cell *matHeaderCellDef>Joined</th>
          <td mat-cell *matCellDef="let r">{{ r.dateOfJoining | istDate: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">{{ r.employmentType }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">
            <app-status-badge [status]="r.active ? 'ACTIVE' : 'INACTIVE'" />
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row)"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align: center; padding: 2rem">
            No employees found
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
    .clickable {
      cursor: pointer;
    }
  `,
})
export class EmployeeListComponent implements OnInit {
  private api = inject(HrmApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);
  private router = inject(Router);

  employmentTypes = EMPLOYMENT_TYPES;
  columns = ['code', 'name', 'designation', 'department', 'joined', 'type', 'status'];
  employees = signal<EmployeeDto[]>([]);
  loading = signal(false);

  searchCtrl = new FormControl('', { nonNullable: true });
  activeCtrl = new FormControl<boolean | null>(true);
  form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(60)]],
    lastName: ['', [Validators.required, Validators.maxLength(60)]],
    designation: [''],
    department: [''],
    dateOfJoining: ['', Validators.required],
    employmentType: this.fb.nonNullable.control<EmploymentType>('PERMANENT'),
    basicPay: this.fb.control<number | null>(null),
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getEmployees(this.searchCtrl.value || null, null, this.activeCtrl.value).subscribe({
      next: (page) => {
        this.employees.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load employees', 'Dismiss', { duration: 3000 });
      },
    });
  }

  create(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.api
      .createEmployee({
        employeeCode: null,
        userId: null,
        firstName: v.firstName,
        lastName: v.lastName,
        email: null,
        phone: null,
        designation: v.designation || null,
        department: v.department || null,
        plantId: null,
        costCentreId: null,
        dateOfJoining: v.dateOfJoining,
        employmentType: v.employmentType,
        payGrade: null,
        basicPay: v.basicPay,
        fixedTravelAllowance: null,
        fixedOtherAllowances: null,
        bankAccountNo: null,
        bankIfsc: null,
        pfNumber: null,
        esiNumber: null,
        defaultShift: null,
        payChangeReason: null,
        version: null,
      })
      .subscribe({
        next: (e) => this.router.navigate(['/hrm/employees', e.id]),
        error: (err) =>
          this.snack.open(err?.error?.detail ?? 'Could not create employee', 'Dismiss', { duration: 5000 }),
      });
  }

  open(e: EmployeeDto): void {
    this.router.navigate(['/hrm/employees', e.id]);
  }
}
