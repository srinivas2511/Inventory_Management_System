import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { problemMessage } from '../../core/models/problem.model';
import { todayIso, toMinorUnits } from './finance-dates';
import { FinanceApi, GlAccountDto, JournalCreateRequest } from './finance.api';

type AccountValue = GlAccountDto | string | null;

/** A line's account must be one picked from the list, not free text that merely looks like an account. */
function accountPicked(control: AbstractControl): ValidationErrors | null {
  const value = control.value as AccountValue;
  return value !== null && typeof value === 'object' && value.id ? null : { account: true };
}

/**
 * Manual journal entry. Money has to balance before the form will save: the running totals show the difference
 * as the lines are typed, and Save stays disabled until debits equal credits on at least two lines.
 */
@Component({
  selector: 'app-journal-form',
  standalone: true,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    MatAutocompleteModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">New journal entry</h2>
      <a mat-button routerLink="/finance/journals">Back to journals</a>
    </div>

    @if (loadingAccounts()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        <div class="head-grid">
          <mat-form-field appearance="outline">
            <mat-label>Posting date</mat-label>
            <input matInput type="date" formControlName="postingDate" />
            @if (form.controls.postingDate.hasError('required') && form.controls.postingDate.touched) {
              <mat-error>Choose a date</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline" class="wide">
            <mat-label>Description</mat-label>
            <input matInput formControlName="description" maxlength="300" />
            @if (form.controls.description.hasError('required') && form.controls.description.touched) {
              <mat-error>Say what this journal is for</mat-error>
            }
          </mat-form-field>
        </div>
        <p class="note">General ledger journal in INR. It stays a draft until it is submitted, approved and posted.</p>

        <div formArrayName="lines" class="lines">
          <div class="line line-head" aria-hidden="true">
            <span>Account</span>
            <span>Debit</span>
            <span>Credit</span>
            <span>Line description</span>
            <span></span>
          </div>
          @for (line of lines.controls; track line; let i = $index) {
            <div class="line" [formGroupName]="i">
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Account {{ i + 1 }}</mat-label>
                <input
                  matInput
                  formControlName="account"
                  [matAutocomplete]="auto"
                  placeholder="Type a code or a name"
                />
                <mat-autocomplete #auto="matAutocomplete" [displayWith]="displayAccount">
                  @for (a of accountOptions(line.controls['account'].value); track a.id) {
                    <mat-option [value]="a">{{ a.accountCode }} · {{ a.name }}</mat-option>
                  }
                </mat-autocomplete>
                @if (line.controls['account'].hasError('account') && line.controls['account'].touched) {
                  <mat-error>Pick an account from the list</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Debit</mat-label>
                <input matInput type="number" step="0.01" min="0" formControlName="debit" />
              </mat-form-field>
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Credit</mat-label>
                <input matInput type="number" step="0.01" min="0" formControlName="credit" />
              </mat-form-field>
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Line description</mat-label>
                <input matInput formControlName="description" maxlength="200" />
              </mat-form-field>
              <button
                mat-icon-button
                type="button"
                [attr.aria-label]="'Remove line ' + (i + 1)"
                [disabled]="lines.length <= 2"
                (click)="removeLine(i)"
              >
                <mat-icon>delete</mat-icon>
              </button>
            </div>
          }
        </div>
        <button mat-stroked-button type="button" (click)="addLine()"><mat-icon>add</mat-icon> Add line</button>

        <section class="totals" [class.balanced]="isBalanced()" aria-live="polite" aria-label="Running balance">
          <span>Debits {{ totalDebit() | number: '1.2-4' }}</span>
          <span>Credits {{ totalCredit() | number: '1.2-4' }}</span>
          <strong>
            @if (isBalanced()) {
              Balanced
            } @else {
              Out of balance by {{ difference() | number: '1.2-4' }}
            }
          </strong>
        </section>

        @if (errorMessage()) {
          <p class="error" role="alert">{{ errorMessage() }}</p>
        }
        @if (blocker(); as why) {
          <p class="hint">{{ why }}</p>
        }
        <div class="actions">
          <button mat-flat-button color="primary" type="submit" [disabled]="saving() || !canSave()">Save draft</button>
          <a mat-button routerLink="/finance/journals">Cancel</a>
        </div>
      </form>
    }
  `,
  styles: [
    `
      .head-grid {
        display: grid;
        grid-template-columns: 220px 1fr;
        gap: 0 12px;
        padding-top: 8px;
      }
      .note {
        margin: 0 0 16px;
        opacity: 0.8;
      }
      .line {
        display: grid;
        grid-template-columns: minmax(220px, 2fr) 1fr 1fr 2fr 48px;
        gap: 8px;
        align-items: start;
        margin-bottom: 8px;
      }
      .line-head {
        font-size: 12px;
        opacity: 0.7;
        margin-bottom: 4px;
      }
      .totals {
        display: flex;
        flex-wrap: wrap;
        gap: 24px;
        margin: 16px 0;
        padding: 12px 16px;
        border: 1px solid var(--mat-sys-outline-variant, #c4c7c5);
        border-radius: 4px;
      }
      .totals.balanced strong {
        color: var(--mat-sys-primary, #1b6ef3);
      }
      .error {
        color: var(--mat-sys-error, #b3261e);
      }
      .hint {
        margin: 0 0 8px;
        opacity: 0.8;
      }
      .actions {
        display: flex;
        gap: 8px;
      }
      mat-form-field {
        width: 100%;
      }
      @media (max-width: 800px) {
        .head-grid,
        .line {
          grid-template-columns: 1fr;
        }
        .line-head {
          display: none;
        }
      }
    `,
  ],
})
export class JournalFormComponent implements OnInit {
  protected readonly accounts = signal<GlAccountDto[]>([]);
  protected readonly loadingAccounts = signal(true);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(FinanceApi);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);

  protected readonly form = this.fb.group({
    postingDate: [todayIso(), Validators.required],
    description: ['', [Validators.required, Validators.pattern(/\S/)]],
    lines: this.fb.array([this.newLine(), this.newLine()]),
  });

  get lines(): FormArray<FormGroup> {
    return this.form.controls.lines as FormArray<FormGroup>;
  }

  ngOnInit(): void {
    this.api.getGlAccounts().subscribe({
      next: (list) => {
        // Only accounts that accept a manual posting; the rest are filled by the system.
        this.accounts.set(list.filter((a) => a.active && a.allowDirectPosting));
        this.loadingAccounts.set(false);
      },
      error: () => {
        this.loadingAccounts.set(false);
        this.errorMessage.set('The chart of accounts could not be loaded. Reload the page to try again.');
      },
    });
  }

  protected displayAccount = (value: AccountValue): string =>
    value !== null && typeof value === 'object' ? `${value.accountCode} · ${value.name}` : (value ?? '');

  protected accountOptions(value: AccountValue): GlAccountDto[] {
    const text = (typeof value === 'string' ? value : '').trim().toLowerCase();
    const all = this.accounts();
    const matches = text
      ? all.filter((a) => a.accountCode.toLowerCase().includes(text) || a.name.toLowerCase().includes(text))
      : all;
    return matches.slice(0, 40);
  }

  protected addLine(): void {
    this.lines.push(this.newLine());
  }

  protected removeLine(index: number): void {
    if (this.lines.length > 2) {
      this.lines.removeAt(index);
    }
  }

  protected totalDebit(): number {
    return this.lines.controls.reduce((sum, l) => sum + Number(l.value.debit ?? 0), 0);
  }

  protected totalCredit(): number {
    return this.lines.controls.reduce((sum, l) => sum + Number(l.value.credit ?? 0), 0);
  }

  protected difference(): number {
    return Math.abs(this.totalDebit() - this.totalCredit());
  }

  protected isBalanced(): boolean {
    return this.totalDebit() > 0 && toMinorUnits(this.totalDebit()) === toMinorUnits(this.totalCredit());
  }

  /** Why Save is off, in the user's terms; null when the journal can be saved. */
  protected blocker(): string | null {
    if (this.lines.controls.some((l) => !this.lineComplete(l))) {
      return 'Every line needs an account from the list and either a debit or a credit. Remove lines you are not using.';
    }
    if (!this.isBalanced()) {
      return 'Debits and credits must be equal before the journal can be saved.';
    }
    return null;
  }

  protected canSave(): boolean {
    return this.form.controls.postingDate.valid && this.form.controls.description.valid && this.blocker() === null;
  }

  protected save(): void {
    if (!this.canSave()) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const request: JournalCreateRequest = {
      journalType: 'GL',
      postingDate: raw.postingDate as string,
      description: (raw.description as string).trim(),
      currencyCode: 'INR',
      lines: this.lines.controls.map((l) => {
        const description = (l.value.description as string | null)?.trim();
        return {
          glAccountId: (l.value.account as GlAccountDto).id,
          debitAmount: Number(l.value.debit ?? 0),
          creditAmount: Number(l.value.credit ?? 0),
          description: description ? description : null,
        };
      }),
    };
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.createJournal(request).subscribe({
      next: (journal) => {
        this.saving.set(false);
        this.snack.open(`Draft ${journal.journalNumber} saved. Submit it for approval when you are ready.`, 'OK', {
          duration: 6000,
        });
        void this.router.navigate(['/finance/journals', journal.id]);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The journal could not be saved.'));
      },
    });
  }

  private lineComplete(line: FormGroup): boolean {
    const debit = Number(line.value.debit ?? 0);
    const credit = Number(line.value.credit ?? 0);
    return line.controls['account'].valid && debit >= 0 && credit >= 0 && debit > 0 !== credit > 0;
  }

  private newLine(): FormGroup {
    const group = this.fb.group({
      account: new FormControl<AccountValue>(null, accountPicked),
      debit: new FormControl<number | null>(null, Validators.min(0)),
      credit: new FormControl<number | null>(null, Validators.min(0)),
      description: [''],
    });
    // A line is a debit or a credit, never both: typing one clears the other.
    group.controls.debit.valueChanges.subscribe((v) => {
      if (Number(v) > 0 && group.controls.credit.value !== null) {
        group.controls.credit.setValue(null, { emitEvent: false });
      }
    });
    group.controls.credit.valueChanges.subscribe((v) => {
      if (Number(v) > 0 && group.controls.debit.value !== null) {
        group.controls.debit.setValue(null, { emitEvent: false });
      }
    });
    return group;
  }
}
