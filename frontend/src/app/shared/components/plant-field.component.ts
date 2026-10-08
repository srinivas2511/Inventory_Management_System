import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { SessionStore } from '../../core/auth/session.store';
import { LookupApi, LookupOption } from '../lookup.api';

/**
 * The plant a new document belongs to. Shown only when the user may choose between plants; with one plant (the usual
 * install) it renders nothing and the server uses that plant. Left empty, the server uses the user's only plant or the
 * default plant, and tells a user with several plants to choose.
 */
@Component({
  selector: 'app-plant-field',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatSelectModule],
  template: `
    @if (plants().length > 1) {
      <mat-form-field appearance="outline" class="plant-field">
        <mat-label>Plant</mat-label>
        <mat-select [formControl]="control()" data-testid="plant-select">
          <mat-option [value]="null">Default plant</mat-option>
          @for (p of plants(); track p.id) {
            <mat-option [value]="p.id">{{ p.code }} · {{ p.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    }
  `,
  styles: [
    `
      .plant-field {
        width: 100%;
      }
    `,
  ],
})
export class PlantFieldComponent implements OnInit {
  readonly control = input.required<FormControl<number | null>>();
  protected readonly plants = signal<LookupOption[]>([]);

  private readonly lookups = inject(LookupApi);
  private readonly session = inject(SessionStore);

  ngOnInit(): void {
    if (!this.session.isLoggedIn()) {
      return; // the lookup needs a signed-in user
    }
    this.lookups.plants().subscribe({ next: (p) => this.plants.set(p), error: () => this.plants.set([]) });
  }
}
