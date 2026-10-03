import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { CrmApi, CrmCustomerDto, TagDto } from './crm.api';

const TAG_GROUPS = ['INDUSTRY', 'REGION', 'TIER', 'STRATEGIC', 'OTHER'];

/** Customer segmentation (CRM-004): filter customers by tag, edit a customer's tags, maintain the tag master. */
@Component({
  selector: 'app-crm-customer-segments',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Customer Segments</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Tag</mat-label>
        <mat-select [formControl]="tagFilter" (selectionChange)="load()">
          <mat-option [value]="null">All customers</mat-option>
          @for (t of tags(); track t.id) {
            <mat-option [value]="t.id">{{ t.tagGroup }}: {{ t.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Search</mat-label>
        <input matInput [formControl]="searchCtrl" (keyup.enter)="load()" />
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>search</mat-icon> Search</button>
    </div>

    @if (editing(); as c) {
      <div class="edit-form">
        <strong>Tags for {{ c.name }}</strong>
        <mat-form-field appearance="outline" class="wide">
          <mat-label>Tags</mat-label>
          <mat-select multiple [formControl]="editTags">
            @for (t of tags(); track t.id) {
              <mat-option [value]="t.id">{{ t.tagGroup }}: {{ t.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <button mat-flat-button color="primary" (click)="saveTags()">Save</button>
        <button mat-button (click)="editing.set(null)">Cancel</button>
      </div>
    }

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="customers()" class="mat-elevation-z1">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let r">{{ r.customerCode }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Customer</th>
          <td mat-cell *matCellDef="let r">{{ r.name }}</td>
        </ng-container>
        <ng-container matColumnDef="tags">
          <th mat-header-cell *matHeaderCellDef>Tags</th>
          <td mat-cell *matCellDef="let r">
            <mat-chip-set>
              @for (t of r.tags; track t.id) {
                <mat-chip>{{ t.tagGroup }}: {{ t.name }}</mat-chip>
              }
            </mat-chip-set>
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button *appHasPermission="'CUSTOMER_TAG_MANAGE'" mat-button (click)="edit(r)">Edit tags</button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No customers found
          </td>
        </tr>
      </table>
    }

    <section *appHasPermission="'CUSTOMER_TAG_MANAGE'" class="tag-master">
      <h3 class="mat-h3">Tag master</h3>
      <div class="edit-form">
        <mat-form-field appearance="outline">
          <mat-label>Group</mat-label>
          <mat-select [formControl]="newGroup">
            @for (g of groups; track g) {
              <mat-option [value]="g">{{ g }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>New tag</mat-label>
          <input matInput maxlength="60" [formControl]="newName" (keyup.enter)="addTag()" />
        </mat-form-field>
        <button mat-flat-button color="primary" [disabled]="!newName.value.trim()" (click)="addTag()">
          <mat-icon>add</mat-icon> Add tag
        </button>
      </div>
      <mat-chip-set>
        @for (t of tags(); track t.id) {
          <mat-chip (removed)="removeTag(t)">
            {{ t.tagGroup }}: {{ t.name }}
            <button matChipRemove aria-label="Delete tag"><mat-icon>cancel</mat-icon></button>
          </mat-chip>
        }
      </mat-chip-set>
    </section>
  `,
  styles: `
    .filter-bar,
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .wide {
      min-width: 320px;
    }
    .tag-master {
      margin-top: 24px;
    }
  `,
})
export class CrmCustomerSegmentsComponent implements OnInit {
  private api = inject(CrmApi);
  private snack = inject(MatSnackBar);

  groups = TAG_GROUPS;
  columns = ['code', 'name', 'tags', 'actions'];
  customers = signal<CrmCustomerDto[]>([]);
  tags = signal<TagDto[]>([]);
  loading = signal(false);
  editing = signal<CrmCustomerDto | null>(null);

  tagFilter = new FormControl<number | null>(null);
  searchCtrl = new FormControl('', { nonNullable: true });
  editTags = new FormControl<number[]>([], { nonNullable: true });
  newGroup = new FormControl('INDUSTRY', { nonNullable: true });
  newName = new FormControl('', { nonNullable: true });

  ngOnInit(): void {
    this.loadTags();
    this.load();
  }

  loadTags(): void {
    this.api.getTags().subscribe({ next: (t) => this.tags.set(t) });
  }

  load(): void {
    this.loading.set(true);
    this.api.getCustomers(this.searchCtrl.value || null, this.tagFilter.value).subscribe({
      next: (page) => {
        this.customers.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load customers', 'Dismiss', { duration: 3000 });
      },
    });
  }

  edit(c: CrmCustomerDto): void {
    this.editTags.setValue(c.tags.map((t) => t.id));
    this.editing.set(c);
  }

  saveTags(): void {
    const c = this.editing();
    if (!c) return;
    this.api.setCustomerTags(c.id, this.editTags.value).subscribe({
      next: () => {
        this.editing.set(null);
        this.load();
      },
      error: (err) => this.snack.open(err?.error?.detail ?? 'Could not save tags', 'Dismiss', { duration: 5000 }),
    });
  }

  addTag(): void {
    const name = this.newName.value.trim();
    if (!name) return;
    this.api.createTag(this.newGroup.value, name).subscribe({
      next: () => {
        this.newName.reset('');
        this.loadTags();
      },
      error: (err) => this.snack.open(err?.error?.detail ?? 'Could not add tag', 'Dismiss', { duration: 5000 }),
    });
  }

  removeTag(t: TagDto): void {
    this.api.deleteTag(t.id).subscribe({
      next: () => this.loadTags(),
      error: (err) => this.snack.open(err?.error?.detail ?? 'Could not delete tag', 'Dismiss', { duration: 5000 }),
    });
  }
}
