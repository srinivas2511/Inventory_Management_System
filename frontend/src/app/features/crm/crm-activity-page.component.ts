import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { CrmActivityTimelineComponent } from './crm-activity-timeline.component';
import { CrmApi, CrmCustomerDto } from './crm.api';

/** The activity log for one customer: everything logged against the customer, its contacts and opportunities. */
@Component({
  selector: 'app-crm-activity-page',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatSelectModule, CrmActivityTimelineComponent],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Customer Activity</h2>
    </div>
    <mat-form-field appearance="outline">
      <mat-label>Customer</mat-label>
      <mat-select [formControl]="customerCtrl">
        @for (c of customers(); track c.id) {
          <mat-option [value]="c.id">{{ c.name }}</mat-option>
        }
      </mat-select>
    </mat-form-field>

    @if (customerCtrl.value; as id) {
      <app-crm-activity-timeline [filter]="{ customerId: id }" />
    } @else {
      <p class="hint">Choose a customer to see and log their calls, emails, meetings and follow-ups.</p>
    }
  `,
  styles: `
    .hint {
      opacity: 0.7;
    }
  `,
})
export class CrmActivityPageComponent implements OnInit {
  private api = inject(CrmApi);

  customers = signal<CrmCustomerDto[]>([]);
  customerCtrl = new FormControl<number | null>(null);

  ngOnInit(): void {
    this.api.getCustomers(null, null, 0, 200).subscribe({ next: (p) => this.customers.set(p.content) });
  }
}
