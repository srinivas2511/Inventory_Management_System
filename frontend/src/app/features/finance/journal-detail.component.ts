import { Component, OnInit, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { FinanceApi, JournalHeaderDto } from './finance.api';

@Component({
  selector: 'app-journal-detail',
  standalone: true,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    StatusBadgeComponent,
  ],
  template: `
    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else if (journal()) {
      <div class="page-header">
        <h2 class="mat-h2">{{ journal()!.journalNumber }}</h2>
        <app-status-badge [status]="journal()!.status" />
      </div>

      <mat-card>
        <mat-card-content>
          <p><strong>Type:</strong> {{ journal()!.journalType }}</p>
          <p><strong>Date:</strong> {{ journal()!.postingDate }}</p>
          <p><strong>Description:</strong> {{ journal()!.description }}</p>
        </mat-card-content>
        <mat-card-actions>
          @if (journal()!.status === 'DRAFT') {
            <button mat-stroked-button (click)="submit()">Submit</button>
          }
          @if (journal()!.status === 'SUBMITTED') {
            <button mat-stroked-button color="accent" (click)="approve()">Approve</button>
          }
          @if (journal()!.status === 'APPROVED') {
            <button mat-flat-button color="primary" (click)="post()">Post</button>
          }
          @if (journal()!.status === 'POSTED') {
            <button mat-stroked-button color="warn" (click)="reverse()">Reverse</button>
          }
        </mat-card-actions>
      </mat-card>

      <h3>Lines</h3>
      <table mat-table [dataSource]="journal()!.lines" class="mat-elevation-z1">
        <ng-container matColumnDef="lineNumber">
          <th mat-header-cell *matHeaderCellDef>#</th>
          <td mat-cell *matCellDef="let l">{{ l.lineNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="accountCode">
          <th mat-header-cell *matHeaderCellDef>Account</th>
          <td mat-cell *matCellDef="let l">{{ l.accountCode }} – {{ l.accountName }}</td>
        </ng-container>
        <ng-container matColumnDef="debitAmount">
          <th mat-header-cell *matHeaderCellDef>Dr</th>
          <td mat-cell *matCellDef="let l">{{ l.debitAmount | number: '1.2-4' }}</td>
        </ng-container>
        <ng-container matColumnDef="creditAmount">
          <th mat-header-cell *matHeaderCellDef>Cr</th>
          <td mat-cell *matCellDef="let l">{{ l.creditAmount | number: '1.2-4' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="lineColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: lineColumns"></tr>
      </table>
    }
  `,
})
export class JournalDetailComponent implements OnInit {
  private api = inject(FinanceApi);
  private route = inject(ActivatedRoute);

  lineColumns = ['lineNumber', 'accountCode', 'debitAmount', 'creditAmount'];
  journal = signal<JournalHeaderDto | null>(null);
  loading = signal(false);

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loading.set(true);
    this.api.getJournal(id).subscribe({
      next: (j) => {
        this.journal.set(j);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  submit() {
    this.action(this.api.submitJournal(this.journal()!.id));
  }
  approve() {
    this.action(this.api.approveJournal(this.journal()!.id));
  }
  post() {
    this.action(this.api.postJournal(this.journal()!.id));
  }
  reverse() {
    this.action(this.api.reverseJournal(this.journal()!.id, 'Manual reversal'));
  }

  private action(obs: Observable<JournalHeaderDto>) {
    obs.subscribe({ next: (j) => this.journal.set(j) });
  }
}
