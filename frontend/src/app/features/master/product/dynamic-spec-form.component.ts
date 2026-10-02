import { Component, Input, OnChanges } from '@angular/core';
import { FormGroup, ReactiveFormsModule, FormControl, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { SpringAttributeDefinitionDto } from './product.api';

@Component({
  selector: 'app-dynamic-spec-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule],
  template: `
    <fieldset [formGroup]="group" class="spec-group">
      <legend class="mat-body-2">{{ title }}</legend>
      @for (attr of attributes; track attr.attributeKey) {
        <mat-form-field appearance="outline" class="spec-field">
          <mat-label>{{ attr.displayName }}{{ attr.unit ? ' (' + attr.unit + ')' : '' }}</mat-label>
          <input
            matInput
            [type]="attr.dataType === 'DECIMAL' || attr.dataType === 'INTEGER' ? 'number' : 'text'"
            [formControlName]="attr.attributeKey"
            [attr.data-testid]="attr.attributeKey"
          />
          @if (group.get(attr.attributeKey)?.hasError('required')) {
            <mat-error>Required</mat-error>
          }
        </mat-form-field>
      }
    </fieldset>
  `,
  styles: [`
    .spec-group { border: 1px solid rgba(0,0,0,.12); border-radius: 4px; padding: 8px 16px; margin: 8px 0; }
    legend { padding: 0 4px; color: rgba(0,0,0,.54); }
    .spec-field { margin-right: 8px; min-width: 140px; }
  `],
})
export class DynamicSpecFormComponent implements OnChanges {
  @Input() group!: FormGroup;
  @Input() attributes: SpringAttributeDefinitionDto[] = [];
  @Input() title = 'Specifications';

  ngOnChanges(): void {
    if (!this.group) return;
    const existing = Object.keys(this.group.controls);
    const incoming = this.attributes.map((a) => a.attributeKey);

    // Remove controls that are no longer in the attribute list
    for (const key of existing) {
      if (!incoming.includes(key)) this.group.removeControl(key);
    }

    // Add new controls
    for (const attr of this.attributes) {
      if (!this.group.contains(attr.attributeKey)) {
        this.group.addControl(
          attr.attributeKey,
          new FormControl(null, attr.required ? Validators.required : []),
        );
      }
    }
  }
}
