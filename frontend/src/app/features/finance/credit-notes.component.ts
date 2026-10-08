import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SessionStore } from '../../core/auth/session.store';
import { problemMessage } from '../../core/models/problem.model';
import { ListPaginatorComponent } from '../../shared/components/list-paginator.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PagedList } from '../../shared/paged-list';
import { CreditNoteDto, FinanceApi } from './finance.api';

/**
 * Customer credit notes (FIN-004). A credit note is created from an invoice on the Accounts Receivable screen. It is a
 * draft until a different user approves it; approving posts the reversing journal and, when goods came back, receives
 * them into quarantine.
 */
@Component({
  selector: 'app-credit-notes',
  standalone: true,
  imports: [
    DecimalPipe,
    HasPermissionDirective,
    ListPaginatorComponent,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    MatTooltipModule,
    RouterLink,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Credit notes</h2>
      <button mat-stroked-button (click)="load()">Refresh</button>
    </div>
    <p class="note">Create a credit note from an invoice on the Accounts Receivable screen.</p>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="notes()" class="mat-elevation-z1" data-testid="credit-note-table">
        <ng-container matColumnDef="creditNoteNumber">
          <th mat-header-cell *matHeaderCellDef>Credit note</th>
          <td mat-cell *matCellDef="let r">{{ r.creditNoteNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="invoiceNumber">
          <th mat-header-cell *matHeaderCellDef>Invoice</th>
          <td mat-cell *matCellDef="let r">{{ r.invoiceNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="creditDate">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let r">{{ r.creditDate }}</td>
        </ng-container>
        <ng-container matColumnDef="subtotal">
          <th mat-header-cell *matHeaderCellDef>Credited</th>
          <td mat-cell *matCellDef="let r">{{ r.subtotal | number: '1.2-4' }} {{ r.currencyCode }}</td>
        </ng-container>
        <ng-container matColumnDef="tax">
          <th mat-header-cell *matHeaderCellDef>Tax reversed</th>
          <td mat-cell *matCellDef="let r">
            {{ r.taxTotal | number: '1.2-4' }}
            @for (l of r.taxLines; track l.taxCode) {
              <div class="tax-line">{{ l.taxType }} {{ l.taxAmount | number: '1.2-4' }}</div>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="totalAmount">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let r">{{ r.totalAmount | number: '1.2-4' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="journal">
          <th mat-header-cell *matHeaderCellDef>Journal</th>
          <td mat-cell *matCellDef="let r">
            @if (r.journalHeaderId) {
              <a [routerLink]="['/finance/journals', r.journalHeaderId]">View</a>
            }
            @if (r.returnTransactionNumber) {
              <div class="tax-line">Return {{ r.returnTransactionNumber }}</div>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            @if (r.status === 'DRAFT') {
              <span
                [matTooltip]="isCreator(r) ? 'A different user has to approve this credit note.' : ''"
                matTooltipPosition="above"
              >
                <button
                  mat-flat-button
                  color="primary"
                  *appHasPermission="'CREDIT_NOTE_APPROVE'"
                  [disabled]="isCreator(r) || busy()"
                  (click)="approve(r)"
                >
                  Approve
                </button>
              </span>
              <button mat-button *appHasPermission="'AR_MANAGE'" [disabled]="busy()" (click)="cancel(r)">Cancel</button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (notes().length === 0) {
        <p class="note">No credit notes yet.</p>
      }
      <app-list-paginator [paged]="paged" (changed)="load()" />
    }
  `,
  styles: [
    `
      .note,
      .tax-line {
        color: var(--mat-sys-on-surface-variant, #49454f);
        font-size: 0.85em;
      }
    `,
  ],
})
export class CreditNotesComponent implements OnInit {
  private readonly api = inject(FinanceApi);
  private readonly snack = inject(MatSnackBar);
  private readonly session = inject(SessionStore);

  protected readonly columns = [
    'creditNoteNumber',
    'invoiceNumber',
    'creditDate',
    'subtotal',
    'tax',
    'totalAmount',
    'status',
    'journal',
    'actions',
  ];
  protected readonly notes = signal<CreditNoteDto[]>([]);
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  readonly paged = new PagedList();

  ngOnInit(): void {
    this.load();
  }

  protected isCreator(note: CreditNoteDto): boolean {
    const userId = this.session.user()?.id;
    return userId != null && note.createdBy === userId;
  }

  load(): void {
    this.loading.set(true);
    this.api.getCreditNotes(this.paged.pageIndex(), this.paged.pageSize()).subscribe({
      next: (p) => {
        this.notes.set(this.paged.apply(p));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected approve(note: CreditNoteDto): void {
    this.act(this.api.approveCreditNote(note.id), `${note.creditNoteNumber} approved and posted`);
  }

  protected cancel(note: CreditNoteDto): void {
    this.act(this.api.cancelCreditNote(note.id), `${note.creditNoteNumber} cancelled`);
  }

  private act(call: ReturnType<FinanceApi['approveCreditNote']>, done: string): void {
    this.busy.set(true);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.snack.open(done, 'OK', { duration: 4000 });
        this.load();
      },
      error: (err) => {
        this.busy.set(false);
        this.snack.open(problemMessage(err, 'The credit note could not be changed.'), 'OK', { duration: 6000 });
      },
    });
  }
}
