import { Component, effect, input, output, untracked } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

export interface FilterOption {
  value: string;
  label: string;
}

export interface FilterDef {
  key: string;
  label: string;
  /** The "no filter" entry is added automatically as "All". */
  options: FilterOption[];
}

/** Search box (debounced) plus select filters. It owns no state: the values come in and changes go out. */
@Component({
  selector: 'app-filter-bar',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <div class="bar">
      <mat-form-field appearance="outline" class="search">
        <mat-label>{{ searchLabel() }}</mat-label>
        <input matInput [formControl]="search" data-testid="search" />
      </mat-form-field>
      @for (f of filters(); track f.key) {
        <mat-form-field appearance="outline">
          <mat-label>{{ f.label }}</mat-label>
          <mat-select
            [value]="valueOf(f.key)"
            (selectionChange)="filterChange.emit({ key: f.key, value: $event.value })"
            [attr.data-testid]="'filter-' + f.key"
          >
            <mat-option value="">All</mat-option>
            @for (o of f.options; track o.value) {
              <mat-option [value]="o.value">{{ o.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      }
    </div>
  `,
  styles: `
    .bar {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }
    .search {
      min-width: 240px;
    }
  `,
})
export class FilterBarComponent {
  readonly q = input('');
  readonly searchLabel = input('Search');
  readonly filters = input<FilterDef[]>([]);
  readonly values = input<Record<string, string>>({});

  readonly searchChange = output<string>();
  readonly filterChange = output<{ key: string; value: string }>();

  protected readonly search = new FormControl('', { nonNullable: true });

  protected valueOf(key: string): string {
    return this.values()[key] || '';
  }

  constructor() {
    effect(() => {
      const incoming = this.q();
      untracked(() => {
        if (incoming !== this.search.value) {
          this.search.setValue(incoming, { emitEvent: false });
        }
      });
    });
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => this.searchChange.emit(value.trim()));
  }
}
