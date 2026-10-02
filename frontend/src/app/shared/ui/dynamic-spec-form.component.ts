import { Component, computed, input } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { AttributeDefinition } from '../../features/master/models';
import { attributePath } from '../forms/dynamic-spec';
import { errorText } from '../forms/server-errors';

/**
 * Renders the inputs for a spring type's attribute definitions (DESIGN.md section 8.4). The controls themselves are
 * created by {@code syncAttributeControls} on the parent form; this component only displays them, so the parent
 * keeps full control over validation, server errors and submission.
 */
@Component({
  selector: 'app-dynamic-spec-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSlideToggleModule],
  template: `
    <div class="grid">
      @for (attribute of ordered(); track attribute.code) {
        @if (control(attribute); as c) {
          @switch (attribute.dataType) {
            @case ('NUMBER') {
              <mat-form-field appearance="outline">
                <mat-label>{{ attribute.label }}{{ attribute.required ? ' *' : '' }}</mat-label>
                <input
                  matInput
                  type="number"
                  step="any"
                  [formControl]="c"
                  [attr.data-testid]="'attr-' + attribute.code"
                />
                @if (attribute.unit) {
                  <span matTextSuffix>{{ attribute.unit }}</span>
                }
                @if (c.touched && c.invalid) {
                  <mat-error>{{ message(c, attribute) }}</mat-error>
                }
              </mat-form-field>
            }
            @case ('ENUM') {
              <mat-form-field appearance="outline">
                <mat-label>{{ attribute.label }}{{ attribute.required ? ' *' : '' }}</mat-label>
                <mat-select [formControl]="c" [attr.data-testid]="'attr-' + attribute.code">
                  <mat-option [value]="null">—</mat-option>
                  @for (v of attribute.enumValues; track v) {
                    <mat-option [value]="v">{{ pretty(v) }}</mat-option>
                  }
                </mat-select>
                @if (c.touched && c.invalid) {
                  <mat-error>{{ message(c, attribute) }}</mat-error>
                }
              </mat-form-field>
            }
            @case ('BOOLEAN') {
              <mat-slide-toggle [formControl]="c" [attr.data-testid]="'attr-' + attribute.code">{{
                attribute.label
              }}</mat-slide-toggle>
            }
            @default {
              <mat-form-field appearance="outline">
                <mat-label>{{ attribute.label }}{{ attribute.required ? ' *' : '' }}</mat-label>
                <input matInput [formControl]="c" [attr.data-testid]="'attr-' + attribute.code" />
                @if (c.touched && c.invalid) {
                  <mat-error>{{ message(c, attribute) }}</mat-error>
                }
              </mat-form-field>
            }
          }
        }
      }
    </div>
  `,
  styles: `
    .grid {
      display: grid;
      gap: 0 16px;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    }
  `,
})
export class DynamicSpecFormComponent {
  readonly form = input.required<FormGroup>();
  readonly attributes = input.required<AttributeDefinition[]>();

  protected readonly ordered = computed(() => [...this.attributes()].sort((a, b) => a.displayOrder - b.displayOrder));

  protected control(attribute: AttributeDefinition): FormControl | null {
    return this.form().get(attributePath(attribute)) as FormControl | null;
  }

  protected pretty(value: string): string {
    const text = value.replace(/_/g, ' ').toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  protected message(control: AbstractControl, attribute: AttributeDefinition): string {
    const errors = control.errors;
    if (errors?.['min']) {
      return `Must be at least ${errors['min'].min}${attribute.unit ? ' ' + attribute.unit : ''}.`;
    }
    if (errors?.['max']) {
      return `Must be at most ${errors['max'].max}${attribute.unit ? ' ' + attribute.unit : ''}.`;
    }
    return errorText(control);
  }
}
