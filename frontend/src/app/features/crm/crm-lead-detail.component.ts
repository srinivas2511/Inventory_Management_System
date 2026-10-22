import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { CrmActivityTimelineComponent } from './crm-activity-timeline.component';
import { CrmApi, LeadDto } from './crm.api';

@Component({
  selector: 'app-crm-lead-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    IstDatePipe,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    StatusBadgeComponent,
    HasPermissionDirective,
    CrmActivityTimelineComponent,
  ],
  template: `
    @if (lead(); as l) {
      <div class="page-header">
        <h2 class="mat-h2">{{ l.leadNumber }} - {{ l.companyName }}</h2>
        <app-status-badge [status]="l.status" />
        <a mat-button routerLink="/crm/leads"><mat-icon>arrow_back</mat-icon> Leads</a>
      </div>

      <dl class="facts">
        <dt>Contact</dt>
        <dd>{{ l.contactName || '-' }}</dd>
        <dt>Source</dt>
        <dd>{{ l.source || '-' }}</dd>
        <dt>Owner</dt>
        <dd>{{ l.ownerName || '-' }}</dd>
        <dt>Estimated value</dt>
        <dd>{{ l.estimatedValue | number: '1.0-2' }}</dd>
        <dt>Expected close</dt>
        <dd>{{ l.expectedCloseDate | istDate: 'dd MMM yyyy' }}</dd>
        @if (l.disqualifyReason) {
          <dt>Disqualified because</dt>
          <dd>{{ l.disqualifyReason }}</dd>
        }
        @if (l.notes) {
          <dt>Notes</dt>
          <dd>{{ l.notes }}</dd>
        }
        @if (l.convertedCustomerId) {
          <dt>Converted</dt>
          <dd>
            {{ l.convertedAt | istDate: 'dd MMM yyyy' }} - customer #{{ l.convertedCustomerId }}
            <a routerLink="/crm/opportunities">View opportunities</a>
          </dd>
        }
      </dl>

      <div *appHasPermission="'LEAD_MANAGE'" class="actions">
        @if (l.allowedActions.includes('CONTACT')) {
          <button mat-flat-button color="primary" (click)="run('contact')">
            <mat-icon>call</mat-icon> Mark contacted
          </button>
        }
        @if (l.allowedActions.includes('QUALIFY')) {
          <button mat-flat-button color="primary" (click)="run('qualify')">
            <mat-icon>verified</mat-icon> Qualify
          </button>
        }
        @if (l.allowedActions.includes('CONVERT')) {
          <mat-form-field appearance="outline" class="narrow">
            <mat-label>Existing customer ID (optional)</mat-label>
            <input matInput type="number" [formControl]="customerCtrl" />
          </mat-form-field>
          <button mat-flat-button color="primary" (click)="convert()">
            <mat-icon>swap_horiz</mat-icon> Convert to customer + opportunity
          </button>
        }
        @if (l.allowedActions.includes('DISQUALIFY')) {
          <mat-form-field appearance="outline" class="narrow">
            <mat-label>Reason</mat-label>
            <input matInput maxlength="300" [formControl]="reasonCtrl" />
          </mat-form-field>
          <button mat-stroked-button color="warn" [disabled]="!reasonCtrl.value.trim()" (click)="disqualify()">
            <mat-icon>cancel</mat-icon> Disqualify
          </button>
        }
      </div>

      <h3 class="mat-h3">Activity</h3>
      <app-crm-activity-timeline [filter]="{ leadId: l.id }" />
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
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      margin-bottom: 16px;
    }
    .narrow {
      width: 260px;
    }
  `,
})
export class CrmLeadDetailComponent implements OnInit {
  private api = inject(CrmApi);
  private route = inject(ActivatedRoute);
  private snack = inject(MatSnackBar);

  lead = signal<LeadDto | null>(null);
  reasonCtrl = new FormControl('', { nonNullable: true });
  customerCtrl = new FormControl<number | null>(null);

  ngOnInit(): void {
    this.load();
  }

  private id(): number {
    return Number(this.route.snapshot.paramMap.get('id'));
  }

  load(): void {
    this.api.getLead(this.id()).subscribe({
      next: (l) => this.lead.set(l),
      error: () => this.snack.open('Failed to load lead', 'Dismiss', { duration: 3000 }),
    });
  }

  run(action: 'contact' | 'qualify'): void {
    const call = action === 'contact' ? this.api.contactLead(this.id()) : this.api.qualifyLead(this.id());
    call.subscribe({
      next: (l) => this.lead.set(l),
      error: (err) => this.snack.open(err?.error?.detail ?? 'Action failed', 'Dismiss', { duration: 5000 }),
    });
  }

  disqualify(): void {
    this.api.disqualifyLead(this.id(), this.reasonCtrl.value.trim()).subscribe({
      next: (l) => this.lead.set(l),
      error: (err) => this.snack.open(err?.error?.detail ?? 'Action failed', 'Dismiss', { duration: 5000 }),
    });
  }

  convert(): void {
    this.api.convertLead(this.id(), this.customerCtrl.value, null).subscribe({
      next: (r) => {
        this.lead.set(r.lead);
        this.snack.open(`Converted: customer ${r.customerCode}, opportunity ${r.opportunity.opportunityNumber}`, 'OK', {
          duration: 6000,
        });
      },
      error: (err) => this.snack.open(err?.error?.detail ?? 'Conversion failed', 'Dismiss', { duration: 5000 }),
    });
  }
}
