import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
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
import {
  EMPLOYMENT_TYPES,
  EmployeeDto,
  EmploymentType,
  HrmApi,
  SKILL_TYPES,
  SkillDto,
  SkillType,
  TRAINING_STATUSES,
  TRAINING_TYPES,
  TrainingDto,
  TrainingStatus,
  TrainingType,
} from './hrm.api';

@Component({
  selector: 'app-employee-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    RouterLink,
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
    @if (employee(); as e) {
      <div class="page-header">
        <a mat-icon-button routerLink="/hrm/employees" aria-label="Back"><mat-icon>arrow_back</mat-icon></a>
        <h2 class="mat-h2">{{ e.employeeCode }} - {{ e.fullName }}</h2>
        <app-status-badge [status]="e.active ? 'ACTIVE' : 'INACTIVE'" />
      </div>

      <dl class="facts">
        <dt>Designation</dt>
        <dd>{{ e.designation || '-' }}</dd>
        <dt>Department</dt>
        <dd>{{ e.department || '-' }}</dd>
        <dt>Joined</dt>
        <dd>
          {{ e.dateOfJoining | date: 'dd MMM yyyy' }} ({{ e.employmentType }})
          @if (e.dateOfLeaving) {
            - left {{ e.dateOfLeaving | date: 'dd MMM yyyy' }}
          }
        </dd>
        <dt>System user</dt>
        <dd>{{ e.username || 'not linked' }}</dd>
        <dt>Default shift</dt>
        <dd>{{ e.defaultShift || '-' }}</dd>
        @if (e.compensationVisible) {
          <dt>Basic pay</dt>
          <dd>{{ e.basicPay | number: '1.2-2' }}</dd>
          <dt>Fixed allowances</dt>
          <dd>
            Travel {{ e.fixedTravelAllowance | number: '1.2-2' }}, other {{ e.fixedOtherAllowances | number: '1.2-2' }}
          </dd>
          <dt>Bank</dt>
          <dd>{{ e.bankAccountNo || '-' }} {{ e.bankIfsc || '' }}</dd>
          <dt>PF / ESI</dt>
          <dd>{{ e.pfNumber || '-' }} / {{ e.esiNumber || '-' }}</dd>
        }
      </dl>

      <form *appHasPermission="'EMPLOYEE_MANAGE'" class="edit-form" [formGroup]="editForm" (ngSubmit)="save()">
        <h3 class="mat-h3 full">Edit</h3>
        <mat-form-field appearance="outline">
          <mat-label>Designation</mat-label>
          <input matInput maxlength="80" formControlName="designation" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Department</mat-label>
          <input matInput maxlength="60" formControlName="department" />
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
          <mat-label>Default shift</mat-label>
          <input matInput maxlength="10" formControlName="defaultShift" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Linked user ID</mat-label>
          <input matInput type="number" formControlName="userId" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Basic pay</mat-label>
          <input matInput type="number" min="0" formControlName="basicPay" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Travel allowance</mat-label>
          <input matInput type="number" min="0" formControlName="fixedTravelAllowance" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Other allowances</mat-label>
          <input matInput type="number" min="0" formControlName="fixedOtherAllowances" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Bank account</mat-label>
          <input matInput maxlength="30" formControlName="bankAccountNo" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>IFSC</mat-label>
          <input matInput maxlength="15" formControlName="bankIfsc" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Reason for pay change</mat-label>
          <input matInput maxlength="300" formControlName="payChangeReason" />
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="editForm.invalid">
          <mat-icon>save</mat-icon> Save
        </button>
        @if (e.active) {
          <mat-form-field appearance="outline">
            <mat-label>Reason for leaving</mat-label>
            <input matInput maxlength="300" formControlName="leavingReason" />
          </mat-form-field>
          <button
            mat-stroked-button
            color="warn"
            type="button"
            [disabled]="!editForm.controls.leavingReason.value.trim()"
            (click)="deactivate()"
          >
            <mat-icon>person_off</mat-icon> Deactivate
          </button>
        }
      </form>

      <h3 class="mat-h3">Skills and authorisations</h3>
      <table mat-table [dataSource]="skills()" class="mat-elevation-z1">
        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let s">{{ s.skillType }}</td>
        </ng-container>
        <ng-container matColumnDef="what">
          <th mat-header-cell *matHeaderCellDef>Machine / operation / certification</th>
          <td mat-cell *matCellDef="let s">{{ s.machineName || s.operationName || s.skillName }}</td>
        </ng-container>
        <ng-container matColumnDef="level">
          <th mat-header-cell *matHeaderCellDef>Level</th>
          <td mat-cell *matCellDef="let s">{{ s.proficiencyLevel }}</td>
        </ng-container>
        <ng-container matColumnDef="expires">
          <th mat-header-cell *matHeaderCellDef>Expires</th>
          <td mat-cell *matCellDef="let s">
            {{ s.expiresOn | date: 'dd MMM yyyy' }}
            @if (s.expired) {
              <app-status-badge status="EXPIRED" />
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let s">
            <button
              *appHasPermission="'EMPLOYEE_MANAGE'"
              mat-icon-button
              aria-label="Remove skill"
              (click)="removeSkill(s)"
            >
              <mat-icon>delete</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="skillColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: skillColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="skillColumns.length" style="text-align: center; padding: 1rem">
            No skills recorded
          </td>
        </tr>
      </table>
      <form *appHasPermission="'EMPLOYEE_MANAGE'" class="edit-form" [formGroup]="skillForm" (ngSubmit)="addSkill()">
        <mat-form-field appearance="outline">
          <mat-label>Skill type</mat-label>
          <mat-select formControlName="skillType">
            @for (t of skillTypes; track t) {
              <mat-option [value]="t">{{ t }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        @if (skillForm.controls.skillType.value === 'MACHINE') {
          <mat-form-field appearance="outline">
            <mat-label>Machine ID</mat-label>
            <input matInput type="number" formControlName="machineId" />
          </mat-form-field>
        }
        @if (skillForm.controls.skillType.value === 'OPERATION') {
          <mat-form-field appearance="outline">
            <mat-label>Operation ID</mat-label>
            <input matInput type="number" formControlName="operationId" />
          </mat-form-field>
        }
        @if (skillForm.controls.skillType.value === 'CERTIFICATION') {
          <mat-form-field appearance="outline">
            <mat-label>Certification</mat-label>
            <input matInput maxlength="80" formControlName="skillName" />
          </mat-form-field>
        }
        <mat-form-field appearance="outline">
          <mat-label>Level (1-5)</mat-label>
          <input matInput type="number" min="1" max="5" formControlName="proficiencyLevel" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Expires</mat-label>
          <input matInput type="date" formControlName="expiresOn" />
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit"><mat-icon>add</mat-icon> Add skill</button>
      </form>

      <h3 class="mat-h3">Training</h3>
      <table mat-table [dataSource]="training()" class="mat-elevation-z1">
        <ng-container matColumnDef="programme">
          <th mat-header-cell *matHeaderCellDef>Programme</th>
          <td mat-cell *matCellDef="let t">{{ t.programmeName }}</td>
        </ng-container>
        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let t">{{ t.trainingType }}</td>
        </ng-container>
        <ng-container matColumnDef="date">
          <th mat-header-cell *matHeaderCellDef>Start</th>
          <td mat-cell *matCellDef="let t">{{ t.startDate | date: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let t"><app-status-badge [status]="t.status" /></td>
        </ng-container>
        <ng-container matColumnDef="renewal">
          <th mat-header-cell *matHeaderCellDef>Renewal due</th>
          <td mat-cell *matCellDef="let t">{{ t.renewalDueOn | date: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let t">
            <button
              *appHasPermission="'EMPLOYEE_MANAGE'"
              mat-icon-button
              aria-label="Delete training"
              (click)="deleteTraining(t)"
            >
              <mat-icon>delete</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="trainingColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: trainingColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="trainingColumns.length" style="text-align: center; padding: 1rem">
            No training recorded
          </td>
        </tr>
      </table>
      <form
        *appHasPermission="'EMPLOYEE_MANAGE'"
        class="edit-form"
        [formGroup]="trainingForm"
        (ngSubmit)="addTraining()"
      >
        <mat-form-field appearance="outline">
          <mat-label>Programme</mat-label>
          <input matInput maxlength="150" formControlName="programmeName" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Type</mat-label>
          <mat-select formControlName="trainingType">
            @for (t of trainingTypes; track t) {
              <mat-option [value]="t">{{ t }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select formControlName="status">
            @for (t of trainingStatuses; track t) {
              <mat-option [value]="t">{{ t }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Start</mat-label>
          <input matInput type="date" formControlName="startDate" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Renewal due</mat-label>
          <input matInput type="date" formControlName="renewalDueOn" />
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="trainingForm.invalid">
          <mat-icon>add</mat-icon> Add training
        </button>
      </form>
    } @else {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    }
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .facts {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 4px 16px;
      margin: 8px 0 16px;
    }
    dt {
      opacity: 0.7;
    }
    dd {
      margin: 0;
    }
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0 16px;
    }
    .full {
      flex-basis: 100%;
      margin: 0;
    }
  `,
})
export class EmployeeDetailComponent implements OnInit {
  private api = inject(HrmApi);
  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private snack = inject(MatSnackBar);

  employmentTypes = EMPLOYMENT_TYPES;
  skillTypes = SKILL_TYPES;
  trainingTypes = TRAINING_TYPES;
  trainingStatuses = TRAINING_STATUSES;
  skillColumns = ['type', 'what', 'level', 'expires', 'actions'];
  trainingColumns = ['programme', 'type', 'date', 'status', 'renewal', 'actions'];

  employee = signal<EmployeeDto | null>(null);
  skills = signal<SkillDto[]>([]);
  training = signal<TrainingDto[]>([]);

  editForm = this.fb.nonNullable.group({
    designation: [''],
    department: [''],
    employmentType: this.fb.nonNullable.control<EmploymentType>('PERMANENT'),
    defaultShift: [''],
    userId: this.fb.control<number | null>(null),
    basicPay: this.fb.control<number | null>(null),
    fixedTravelAllowance: this.fb.control<number | null>(null),
    fixedOtherAllowances: this.fb.control<number | null>(null),
    bankAccountNo: [''],
    bankIfsc: [''],
    payChangeReason: [''],
    leavingReason: [''],
  });
  skillForm = this.fb.nonNullable.group({
    skillType: this.fb.nonNullable.control<SkillType>('MACHINE'),
    machineId: this.fb.control<number | null>(null),
    operationId: this.fb.control<number | null>(null),
    skillName: [''],
    proficiencyLevel: this.fb.control<number | null>(null),
    expiresOn: [''],
  });
  trainingForm = this.fb.nonNullable.group({
    programmeName: ['', [Validators.required, Validators.maxLength(150)]],
    trainingType: this.fb.nonNullable.control<TrainingType>('INTERNAL'),
    status: this.fb.nonNullable.control<TrainingStatus>('PLANNED'),
    startDate: ['', Validators.required],
    renewalDueOn: [''],
  });

  private get id(): number {
    return Number(this.route.snapshot.paramMap.get('id'));
  }

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.api.getEmployee(this.id).subscribe({
      next: (e) => {
        this.employee.set(e);
        this.editForm.patchValue({
          designation: e.designation ?? '',
          department: e.department ?? '',
          employmentType: e.employmentType,
          defaultShift: e.defaultShift ?? '',
          userId: e.userId,
          basicPay: e.basicPay,
          fixedTravelAllowance: e.fixedTravelAllowance,
          fixedOtherAllowances: e.fixedOtherAllowances,
          bankAccountNo: e.bankAccountNo ?? '',
          bankIfsc: e.bankIfsc ?? '',
          payChangeReason: '',
          leavingReason: '',
        });
      },
      error: () => this.snack.open('Failed to load employee', 'Dismiss', { duration: 3000 }),
    });
    this.api.getSkills(this.id).subscribe({ next: (s) => this.skills.set(s) });
    this.api.getTraining(this.id).subscribe({ next: (t) => this.training.set(t) });
  }

  save(): void {
    const e = this.employee();
    if (!e) return;
    const v = this.editForm.getRawValue();
    this.api
      .updateEmployee(e.id, {
        employeeCode: e.employeeCode,
        userId: v.userId,
        firstName: e.firstName,
        lastName: e.lastName,
        email: e.email,
        phone: e.phone,
        designation: v.designation || null,
        department: v.department || null,
        plantId: e.plantId,
        costCentreId: e.costCentreId,
        dateOfJoining: e.dateOfJoining,
        employmentType: v.employmentType,
        payGrade: e.payGrade,
        basicPay: v.basicPay,
        fixedTravelAllowance: v.fixedTravelAllowance,
        fixedOtherAllowances: v.fixedOtherAllowances,
        bankAccountNo: v.bankAccountNo || null,
        bankIfsc: v.bankIfsc || null,
        pfNumber: e.pfNumber,
        esiNumber: e.esiNumber,
        defaultShift: v.defaultShift || null,
        payChangeReason: v.payChangeReason || null,
        version: e.version,
      })
      .subscribe({
        next: () => {
          this.snack.open('Employee saved', 'Dismiss', { duration: 2000 });
          this.reload();
        },
        error: (err) => this.snack.open(err?.error?.detail ?? 'Could not save', 'Dismiss', { duration: 5000 }),
      });
  }

  deactivate(): void {
    const e = this.employee();
    if (!e) return;
    this.api.deactivateEmployee(e.id, this.editForm.controls.leavingReason.value.trim(), null).subscribe({
      next: () => this.reload(),
      error: (err) => this.snack.open(err?.error?.detail ?? 'Could not deactivate', 'Dismiss', { duration: 5000 }),
    });
  }

  addSkill(): void {
    const v = this.skillForm.getRawValue();
    this.api
      .addSkill(this.id, {
        skillType: v.skillType,
        machineId: v.skillType === 'MACHINE' ? v.machineId : null,
        operationId: v.skillType === 'OPERATION' ? v.operationId : null,
        skillName: v.skillType === 'CERTIFICATION' ? v.skillName || null : null,
        certificationNo: null,
        proficiencyLevel: v.proficiencyLevel,
        certifiedOn: null,
        expiresOn: v.expiresOn || null,
        active: true,
      })
      .subscribe({
        next: () => this.api.getSkills(this.id).subscribe((s) => this.skills.set(s)),
        error: (err) => this.snack.open(err?.error?.detail ?? 'Could not add skill', 'Dismiss', { duration: 5000 }),
      });
  }

  removeSkill(s: SkillDto): void {
    this.api.removeSkill(this.id, s.id).subscribe({
      next: () => this.skills.update((list) => list.filter((x) => x.id !== s.id)),
      error: () => this.snack.open('Could not remove skill', 'Dismiss', { duration: 3000 }),
    });
  }

  addTraining(): void {
    if (this.trainingForm.invalid) return;
    const v = this.trainingForm.getRawValue();
    this.api
      .addTraining(this.id, {
        programmeName: v.programmeName,
        trainingType: v.trainingType,
        provider: null,
        startDate: v.startDate,
        endDate: null,
        hours: null,
        status: v.status,
        score: null,
        certificateNo: null,
        certifiedOn: null,
        renewalDueOn: v.renewalDueOn || null,
        notes: null,
        version: null,
      })
      .subscribe({
        next: () => {
          this.trainingForm.reset({
            programmeName: '',
            trainingType: 'INTERNAL',
            status: 'PLANNED',
            startDate: '',
            renewalDueOn: '',
          });
          this.api.getTraining(this.id).subscribe((t) => this.training.set(t));
        },
        error: (err) => this.snack.open(err?.error?.detail ?? 'Could not add training', 'Dismiss', { duration: 5000 }),
      });
  }

  deleteTraining(t: TrainingDto): void {
    this.api.deleteTraining(this.id, t.id).subscribe({
      next: () => this.training.update((list) => list.filter((x) => x.id !== t.id)),
      error: () => this.snack.open('Could not delete training', 'Dismiss', { duration: 3000 }),
    });
  }
}
