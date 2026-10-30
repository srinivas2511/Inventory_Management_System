import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { Observable } from 'rxjs';
import { SessionStore } from '../../core/auth/session.store';
import { problemMessage } from '../../core/models/problem.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { FinanceApi, FiscalPeriodDto, JournalHeaderDto } from './finance.api';
import { ReasonDialogComponent } from './reason-dialog.component';

@Component({
  selector: 'app-journal-detail',
  standalone: true,
  imports: [
    IstDatePipe,
    DecimalPipe,
    HasPermissionDirective,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    MatTooltipModule,
    RouterLink,
    StatusBadgeComponent,
  ],
  template: `
    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      @if (journal(); as j) {
        <div class="page-header">
          <h2 class="mat-h2">{{ j.journalNumber }}</h2>
          <app-status-badge [status]="j.status" />
        </div>

        <mat-card>
          <mat-card-content>
            <p><strong>Type:</strong> {{ j.journalType }}</p>
            <p><strong>Posting date:</strong> {{ j.postingDate }}</p>
            <p><strong>Description:</strong> {{ j.description }}</p>
            <p><strong>Currency:</strong> {{ j.currencyCode }}</p>
            @if (period(); as p) {
              <p>
                <strong>Period:</strong> {{ p.periodName }}
                <app-status-badge [status]="p.status" />
              </p>
              @if (p.status !== 'OPEN') {
                <p class="warning" role="status">
                  This period is {{ p.status.toLowerCase() }}. Posting into it, or reversing a journal posted in it, is
                  refused until a finance manager reopens it.
                </p>
              }
            }
            @if (j.sourceDocumentType) {
              <p>
                <strong>Source:</strong> {{ j.sourceModule }} · {{ j.sourceDocumentType }} #{{ j.sourceDocumentId }}
              </p>
            }
            @if (j.reversalOfId) {
              <p>
                <strong>Reverses:</strong>
                <a [routerLink]="['/finance/journals', j.reversalOfId]">journal #{{ j.reversalOfId }}</a>
              </p>
            }
            <p class="trail">
              Prepared by {{ j.preparedByUsername ?? ('user #' + j.preparedBy) }}
              @if (j.approvedBy) {
                · approved by {{ j.approvedByUsername ?? ('user #' + j.approvedBy) }}
                @if (j.approvedAt) {
                  {{ j.approvedAt | istDate: 'medium' }}
                }
              }
              @if (j.postedBy) {
                · posted by {{ j.postedByUsername ?? ('user #' + j.postedBy) }}
                @if (j.postedAt) {
                  {{ j.postedAt | istDate: 'medium' }}
                }
              }
            </p>
            @if (errorMessage()) {
              <p class="error" role="alert">{{ errorMessage() }}</p>
            }
          </mat-card-content>
          <mat-card-actions>
            @if (j.status === 'DRAFT') {
              <button mat-stroked-button *appHasPermission="'GL_POST'" [disabled]="working()" (click)="submit()">
                Submit for approval
              </button>
            }
            @if (j.status === 'SUBMITTED') {
              <span
                *appHasPermission="'JOURNAL_APPROVE'"
                [matTooltip]="ownJournal() ? 'You prepared this journal, so another approver must approve it' : ''"
              >
                <button mat-stroked-button color="accent" [disabled]="working() || ownJournal()" (click)="approve()">
                  Approve
                </button>
              </span>
            }
            @if (j.status === 'APPROVED') {
              <button
                mat-flat-button
                color="primary"
                *appHasPermission="'GL_POST'"
                [disabled]="working()"
                (click)="post()"
              >
                Post to ledger
              </button>
            }
            @if (j.status === 'POSTED') {
              <button
                mat-stroked-button
                color="warn"
                *appHasPermission="'GL_POST'"
                [disabled]="working()"
                (click)="reverse()"
              >
                Reverse
              </button>
            }
          </mat-card-actions>
        </mat-card>

        <h3>Lines</h3>
        <table mat-table [dataSource]="j.lines" class="mat-elevation-z1">
          <ng-container matColumnDef="lineNumber">
            <th mat-header-cell *matHeaderCellDef>#</th>
            <td mat-cell *matCellDef="let l">{{ l.lineNumber }}</td>
            <td mat-footer-cell *matFooterCellDef>Total</td>
          </ng-container>
          <ng-container matColumnDef="accountCode">
            <th mat-header-cell *matHeaderCellDef>Account</th>
            <td mat-cell *matCellDef="let l">{{ l.accountCode }} – {{ l.accountName }}</td>
            <td mat-footer-cell *matFooterCellDef></td>
          </ng-container>
          <ng-container matColumnDef="description">
            <th mat-header-cell *matHeaderCellDef>Description</th>
            <td mat-cell *matCellDef="let l">{{ l.description ?? '' }}</td>
            <td mat-footer-cell *matFooterCellDef></td>
          </ng-container>
          <ng-container matColumnDef="debitAmount">
            <th mat-header-cell *matHeaderCellDef>Dr</th>
            <td mat-cell *matCellDef="let l">{{ l.debitAmount | number: '1.2-4' }}</td>
            <td mat-footer-cell *matFooterCellDef>{{ totalDebit() | number: '1.2-4' }}</td>
          </ng-container>
          <ng-container matColumnDef="creditAmount">
            <th mat-header-cell *matHeaderCellDef>Cr</th>
            <td mat-cell *matCellDef="let l">{{ l.creditAmount | number: '1.2-4' }}</td>
            <td mat-footer-cell *matFooterCellDef>{{ totalCredit() | number: '1.2-4' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="lineColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: lineColumns"></tr>
          <tr mat-footer-row *matFooterRowDef="lineColumns"></tr>
        </table>
      } @else {
        <p role="alert" class="error">{{ errorMessage() || 'The journal could not be loaded.' }}</p>
      }
    }
  `,
  styles: [
    `
      .warning {
        padding: 8px 12px;
        border-left: 4px solid var(--mat-sys-tertiary, #7d5260);
      }
      .trail {
        opacity: 0.8;
        font-size: 13px;
      }
      .error {
        color: var(--mat-sys-error, #b3261e);
      }
    `,
  ],
})
export class JournalDetailComponent implements OnInit {
  private api = inject(FinanceApi);
  private session = inject(SessionStore);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);

  lineColumns = ['lineNumber', 'accountCode', 'description', 'debitAmount', 'creditAmount'];
  journal = signal<JournalHeaderDto | null>(null);
  periods = signal<FiscalPeriodDto[]>([]);
  loading = signal(false);
  working = signal(false);
  errorMessage = signal('');

  /** Business rule 19: the preparer cannot approve the journal (the server enforces this too). */
  ownJournal = computed(() => {
    const userId = this.session.user()?.id;
    return userId != null && this.journal()?.preparedBy === userId;
  });
  period = computed(() => this.periods().find((p) => p.id === this.journal()?.periodId) ?? null);
  totalDebit = computed(() => (this.journal()?.lines ?? []).reduce((sum, l) => sum + Number(l.debitAmount), 0));
  totalCredit = computed(() => (this.journal()?.lines ?? []).reduce((sum, l) => sum + Number(l.creditAmount), 0));

  ngOnInit() {
    // Reversing opens the reversing journal in this same screen, so follow the id rather than read it once.
    this.route.paramMap.subscribe((params) => this.load(Number(params.get('id'))));
    this.api.getPeriods().subscribe({ next: (p) => this.periods.set(p) });
  }

  submit() {
    this.action(this.api.submitJournal(this.journal()!.id), 'submitted for approval');
  }

  approve() {
    this.action(this.api.approveJournal(this.journal()!.id), 'approved');
  }

  post() {
    this.action(this.api.postJournal(this.journal()!.id), 'posted to the ledger');
  }

  reverse() {
    const journal = this.journal()!;
    this.dialog
      .open(ReasonDialogComponent, {
        data: {
          title: `Reverse ${journal.journalNumber}?`,
          message:
            'A reversing journal is posted today with every debit and credit swapped. This journal is then marked reversed and cannot be reversed again.',
          label: 'Reason for reversing',
          confirmLabel: 'Reverse journal',
          maxLength: 200,
        },
      })
      .afterClosed()
      .subscribe((reason?: string) => {
        if (!reason) {
          return;
        }
        this.working.set(true);
        this.errorMessage.set('');
        this.api.reverseJournal(journal.id, reason).subscribe({
          next: (reversal) => {
            this.working.set(false);
            this.snack.open(`${journal.journalNumber} reversed by ${reversal.journalNumber}`, 'OK', {
              duration: 6000,
            });
            void this.router.navigate(['/finance/journals', reversal.id]);
          },
          error: (e) => {
            this.working.set(false);
            this.errorMessage.set(problemMessage(e, 'The journal could not be reversed.'));
          },
        });
      });
  }

  private load(id: number) {
    this.loading.set(true);
    this.errorMessage.set('');
    this.api.getJournal(id).subscribe({
      next: (j) => {
        this.journal.set(j);
        this.loading.set(false);
      },
      error: (e) => {
        this.journal.set(null);
        this.errorMessage.set(problemMessage(e, 'The journal could not be loaded.'));
        this.loading.set(false);
      },
    });
  }

  private action(call: Observable<JournalHeaderDto>, done: string) {
    this.working.set(true);
    this.errorMessage.set('');
    call.subscribe({
      next: (j) => {
        this.working.set(false);
        this.journal.set(j);
        this.snack.open(`${j.journalNumber} ${done}`, 'OK', { duration: 4000 });
      },
      error: (e) => {
        this.working.set(false);
        this.errorMessage.set(problemMessage(e, `The journal could not be ${done}.`));
      },
    });
  }
}
