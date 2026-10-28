import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

export interface ItemCategoryDto {
  id: number;
  categoryCode: string;
  name: string;
  parentId: number | null;
  itemType: 'MATERIAL' | 'PRODUCT' | 'BOTH';
  active: boolean;
}

@Component({
  selector: 'app-item-categories',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2" i18n="@@item-categories.title">Item Categories</h2>
      <button mat-flat-button color="primary" (click)="startCreate()" [disabled]="creating()">
        <mat-icon aria-hidden="true">add</mat-icon> Add Category
      </button>
    </div>

    @if (creating()) {
      <form [formGroup]="form" (ngSubmit)="save()" class="inline-form mat-elevation-z2">
        <mat-form-field>
          <mat-label>Category Code</mat-label>
          <input matInput formControlName="categoryCode" placeholder="CAT-001" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Item Type</mat-label>
          <mat-select formControlName="itemType">
            <mat-option value="MATERIAL">Material</mat-option>
            <mat-option value="PRODUCT">Product</mat-option>
            <mat-option value="BOTH">Both</mat-option>
          </mat-select>
        </mat-form-field>
        <div class="form-actions">
          <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">Save</button>
          <button mat-button type="button" (click)="creating.set(false)">Cancel</button>
        </div>
      </form>
    }

    @if (loading()) {
      <mat-spinner diameter="32"></mat-spinner>
    } @else {
      <table mat-table [dataSource]="categories()" class="full-width">
        <ng-container matColumnDef="categoryCode">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let row">{{ row.categoryCode }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let row">{{ row.name }}</td>
        </ng-container>
        <ng-container matColumnDef="itemType">
          <th mat-header-cell *matHeaderCellDef>Item Type</th>
          <td mat-cell *matCellDef="let row">{{ row.itemType }}</td>
        </ng-container>
        <ng-container matColumnDef="active">
          <th mat-header-cell *matHeaderCellDef>Active</th>
          <td mat-cell *matCellDef="let row">{{ row.active ? 'Yes' : 'No' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let row; columns: cols"></tr>
      </table>
    }
  `,
  styles: [
    `
      .page-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 16px;
      }
      .inline-form {
        padding: 16px;
        margin-bottom: 16px;
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: flex-start;
      }
      .form-actions {
        display: flex;
        gap: 8px;
        align-items: center;
        padding-top: 8px;
      }
      .full-width {
        width: 100%;
      }
    `,
  ],
})
export class ItemCategoriesComponent implements OnInit {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  categories = signal<ItemCategoryDto[]>([]);
  loading = signal(false);
  creating = signal(false);
  cols = ['categoryCode', 'name', 'itemType', 'active'];

  form = this.fb.group({
    categoryCode: ['', [Validators.required, Validators.maxLength(20)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    itemType: ['BOTH', Validators.required],
  });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.http.get<ItemCategoryDto[]>('/api/admin/item-categories').subscribe({
      next: (data) => {
        this.categories.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load categories', 'Close', { duration: 3000 });
      },
    });
  }

  startCreate() {
    this.form.reset({ itemType: 'BOTH' });
    this.creating.set(true);
  }

  save() {
    if (this.form.invalid) return;
    this.http.post<ItemCategoryDto>('/api/admin/item-categories', this.form.value).subscribe({
      next: (created) => {
        this.categories.update((arr) => [...arr, created]);
        this.creating.set(false);
        this.snack.open('Category created', 'Close', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to create category', 'Close', { duration: 3000 }),
    });
  }
}
