import { Component, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { problemMessage } from '../../core/models/problem.model';
import { ProductionExecutionApi } from './production-execution.api';
import { AssignableOperatorDto, ProductionOperationDto } from './production-order.model';

export interface AssignDialogData {
  orderId: number;
  orderNumber: string;
  /** The operation to assign; null assigns every unfinished operation of the order. */
  operation: ProductionOperationDto | null;
}

/** Puts production work in front of an operator: operators and supervisors only see operations assigned to them. */
@Component({
  selector: 'app-assign-operator-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>
      @if (data.operation) {
        Assign operator: operation {{ data.operation.sequenceNo }} of {{ data.orderNumber }}
      } @else {
        Assign operator to all open operations of {{ data.orderNumber }}
      }
    </h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="assign-form" (ngSubmit)="save()" novalidate>
        <mat-form-field appearance="outline" class="wide">
          <mat-label>Search people</mat-label>
          <input matInput [formControl]="search" />
          <mat-hint>Only active users who can execute production work are listed</mat-hint>
        </mat-form-field>
        <mat-form-field appearance="outline" class="wide">
          <mat-label>Operator</mat-label>
          <mat-select formControlName="operatorId">
            <mat-option [value]="null">Unassigned</mat-option>
            @for (o of options(); track o.id) {
              <mat-option [value]="o.id">{{ o.fullName }} ({{ o.username }})</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" class="wide">
          <mat-label>Reason (optional)</mat-label>
          <input matInput formControlName="reason" />
        </mat-form-field>
        @if (errorMessage()) {
          <p class="form-error mat-body-2" role="alert">{{ errorMessage() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="assign-form" type="submit" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Save assignment
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .wide {
        width: 100%;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
    `,
  ],
})
export class AssignOperatorDialogComponent implements OnInit {
  protected readonly data = inject<AssignDialogData>(MAT_DIALOG_DATA);
  protected readonly options = signal<AssignableOperatorDto[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly search = new FormControl('', { nonNullable: true });

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ProductionExecutionApi);
  private readonly dialogRef = inject(MatDialogRef<AssignOperatorDialogComponent>);

  protected readonly form = this.fb.nonNullable.group({
    operatorId: this.fb.control<number | null>(this.data.operation?.assignedOperatorId ?? null),
    reason: [''],
  });

  constructor() {
    this.search.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) => this.api.assignableOperators(q.trim())),
        takeUntilDestroyed(),
      )
      .subscribe((list) => this.options.set(this.withCurrent(list)));
  }

  ngOnInit(): void {
    this.api.assignableOperators().subscribe((list) => this.options.set(this.withCurrent(list)));
  }

  /** Keeps the person already assigned selectable even if they no longer match the search or the role rule. */
  private withCurrent(list: AssignableOperatorDto[]): AssignableOperatorDto[] {
    const current = this.data.operation;
    if (current?.assignedOperatorId && !list.some((o) => o.id === current.assignedOperatorId)) {
      return [
        { id: current.assignedOperatorId, username: '', fullName: current.assignedOperatorName ?? 'Current operator' },
        ...list,
      ];
    }
    return list;
  }

  protected save(): void {
    const raw = this.form.getRawValue();
    const request = { operatorId: raw.operatorId, reason: raw.reason.trim() || null };
    this.saving.set(true);
    this.errorMessage.set('');
    const observer = {
      next: () => {
        this.saving.set(false);
        this.dialogRef.close(true);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The assignment could not be saved.'));
      },
    };
    if (this.data.operation) {
      this.api.assignOperation(this.data.operation.id, request).subscribe(observer);
    } else {
      this.api.assignOrder(this.data.orderId, request).subscribe(observer);
    }
  }
}
