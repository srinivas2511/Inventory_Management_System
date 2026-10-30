import {
  Component,
  Input,
  OnChanges,
  Output,
  EventEmitter,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';

export interface CustomFieldDef {
  id: number;
  entityType: string;
  fieldKey: string;
  label: string;
  fieldType: string;
  options: string | null;
  mandatory: boolean;
  showInList: boolean;
  sortOrder: number;
  active: boolean;
}

@Component({
  selector: 'app-custom-fields-panel',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatDatepickerModule,
    MatNativeDateModule,
  ],
  template: `
    <mat-expansion-panel>
      <mat-expansion-panel-header>
        <mat-panel-title>Custom Fields</mat-panel-title>
      </mat-expansion-panel-header>

      @if (definitions.length === 0) {
        <p class="hint">No custom fields defined for this entity.</p>
      }

      <form [formGroup]="form" (ngSubmit)="$event.preventDefault()">
        @for (def of definitions; track def.id) {
          <div class="field-row">
            @if (def.fieldType === 'TEXT') {
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>{{ def.label }}</mat-label>
                <input matInput [formControlName]="def.fieldKey" (change)="emitChange()" />
              </mat-form-field>
            }
            @if (def.fieldType === 'NUMBER') {
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>{{ def.label }}</mat-label>
                <input
                  matInput
                  type="number"
                  [formControlName]="def.fieldKey"
                  (change)="emitChange()"
                />
              </mat-form-field>
            }
            @if (def.fieldType === 'DATE') {
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>{{ def.label }}</mat-label>
                <input
                  matInput
                  [matDatepicker]="picker"
                  [formControlName]="def.fieldKey"
                  (dateChange)="emitChange()"
                />
                <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
                <mat-datepicker #picker></mat-datepicker>
              </mat-form-field>
            }
            @if (def.fieldType === 'BOOLEAN') {
              <div class="toggle-row">
                <mat-slide-toggle
                  [formControlName]="def.fieldKey"
                  (change)="emitChange()"
                >{{ def.label }}</mat-slide-toggle>
              </div>
            }
            @if (def.fieldType === 'SELECT') {
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>{{ def.label }}</mat-label>
                <mat-select [formControlName]="def.fieldKey" (selectionChange)="emitChange()">
                  @for (opt of parseOptions(def.options); track opt) {
                    <mat-option [value]="opt">{{ opt }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
            }
          </div>
        }
      </form>
    </mat-expansion-panel>
  `,
  styles: [
    `
      .hint {
        color: rgba(0, 0, 0, 0.54);
        margin: 8px 0;
      }
      .full-width {
        width: 100%;
      }
      .field-row {
        margin: 4px 0;
      }
      .toggle-row {
        margin: 12px 0;
      }
    `,
  ],
})
export class CustomFieldsPanelComponent implements OnChanges {
  @Input() entityType = '';
  @Input() entityId = 0;
  @Output() valuesChange = new EventEmitter<Record<string, unknown>>();

  protected definitions: CustomFieldDef[] = [];
  protected form: FormGroup;

  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);

  constructor() {
    this.form = this.fb.group({});
  }

  ngOnChanges(): void {
    if (this.entityId > 0 && this.entityType) {
      this.load();
    }
  }

  private load(): void {
    this.http
      .get<CustomFieldDef[]>(`/api/admin/custom-fields?entityType=${this.entityType}`)
      .subscribe({
        next: (defs) => {
          this.definitions = defs.filter((d) => d.active);
          this.buildForm(this.definitions);
        },
        // eslint-disable-next-line @typescript-eslint/no-empty-function
        error: () => {},
      });
  }

  private buildForm(defs: CustomFieldDef[]): void {
    const controls: Record<string, unknown> = {};
    for (const def of defs) {
      const defaultValue = def.fieldType === 'BOOLEAN' ? false : '';
      controls[def.fieldKey] = [defaultValue];
    }
    this.form = this.fb.group(controls);
  }

  protected parseOptions(options: string | null): string[] {
    if (!options) return [];
    return options
      .split(',')
      .map((o) => o.trim())
      .filter(Boolean);
  }

  protected emitChange(): void {
    this.valuesChange.emit(this.form.value as Record<string, unknown>);
  }
}
