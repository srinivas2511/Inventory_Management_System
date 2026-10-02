import { Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Observable, catchError, debounceTime, of, switchMap } from 'rxjs';

export interface LookupItem {
  id: number;
  code: string;
  name: string;
}

/**
 * A typeahead picker for a record by code or name (customer, material, ...). It is a form control whose value is
 * the chosen item or null, so it takes part in validation and dirty tracking like any input. Typing without
 * choosing leaves the previous value in place; clearing the text clears the value.
 */
@Component({
  selector: 'app-lookup-picker',
  standalone: true,
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => LookupPickerComponent), multi: true }],
  template: `
    <mat-form-field appearance="outline" class="full">
      <mat-label>{{ label() }}</mat-label>
      <input
        matInput
        [formControl]="text"
        [matAutocomplete]="auto"
        (blur)="onTouched()"
        [attr.data-testid]="testId()"
      />
      <mat-autocomplete #auto="matAutocomplete" [displayWith]="display" (optionSelected)="picked($event)">
        @for (item of results(); track item.id) {
          <mat-option [value]="item">{{ item.code }} — {{ item.name }}</mat-option>
        }
        @if (!results().length && searched()) {
          <mat-option disabled>No match</mat-option>
        }
      </mat-autocomplete>
      <ng-content select="mat-hint" />
    </mat-form-field>
  `,
  styles: `
    .full {
      width: 100%;
    }
  `,
})
export class LookupPickerComponent implements ControlValueAccessor {
  readonly label = input.required<string>();
  readonly search = input.required<(q: string) => Observable<LookupItem[]>>();
  readonly testId = input('lookup');

  protected readonly text = new FormControl<string | LookupItem>('', { nonNullable: true });
  protected readonly results = signal<LookupItem[]>([]);
  protected readonly searched = signal(false);

  private value: LookupItem | null = null;
  private onChange: (value: LookupItem | null) => void = () => undefined;
  protected onTouched: () => void = () => undefined;

  protected readonly display = (item: LookupItem | string | null): string =>
    item && typeof item === 'object' ? `${item.code} — ${item.name}` : (item ?? '');

  constructor() {
    this.text.valueChanges
      .pipe(
        debounceTime(250),
        switchMap((typed) => {
          if (typeof typed !== 'string') {
            return of(null);
          }
          if (!typed.trim() && this.value) {
            this.value = null;
            this.onChange(null);
          }
          return this.search()(typed.trim()).pipe(catchError(() => of([] as LookupItem[])));
        }),
      )
      .subscribe((items) => {
        if (items) {
          this.results.set(items);
          this.searched.set(true);
        }
      });
  }

  writeValue(value: LookupItem | null): void {
    this.value = value;
    this.text.setValue(value ?? '', { emitEvent: false });
  }

  registerOnChange(fn: (value: LookupItem | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    if (disabled) {
      this.text.disable({ emitEvent: false });
    } else {
      this.text.enable({ emitEvent: false });
    }
  }

  protected picked(event: MatAutocompleteSelectedEvent): void {
    this.value = event.option.value as LookupItem;
    this.onChange(this.value);
  }
}
