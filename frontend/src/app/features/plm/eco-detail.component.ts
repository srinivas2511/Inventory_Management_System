import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Observable } from 'rxjs';
import { SessionStore } from '../../core/auth/session.store';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import {
  ECO_CATEGORIES,
  ECO_ITEM_TYPES,
  ECO_URGENCIES,
  EcoChangeCategory,
  EcoDto,
  EcoItemType,
  EcoUrgency,
  PlmApi,
  ProductLifecycleRowDto,
  SaveEcoItemRequest,
} from './plm.api';
import { DocumentAttachmentsComponent } from '../documents/document-attachments.component';

interface ItemRow extends SaveEcoItemRequest {
  productLabel: string;
  oldBomLabel: string | null;
  newBomLabel: string | null;
}

@Component({
  selector: 'app-eco-detail',
  standalone: true,
  imports: [
    DocumentAttachmentsComponent,
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
  ],
  template: `
    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <div class="page-header">
        <h2 class="mat-h2">{{ eco() ? eco()!.ecoNumber + ' - ' + eco()!.title : 'New engineering change order' }}</h2>
        @if (eco(); as e) {
          <app-status-badge [status]="e.status" />
        }
        <a mat-button routerLink="/plm/ecos"><mat-icon>arrow_back</mat-icon> ECOs</a>
      </div>

      @if (editable()) {
        <form class="edit-form" [formGroup]="form" (ngSubmit)="save()">
          <mat-form-field appearance="outline" class="wide">
            <mat-label>Title</mat-label>
            <input matInput maxlength="200" formControlName="title" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Category</mat-label>
            <mat-select formControlName="changeCategory">
              @for (c of categories; track c) {
                <mat-option [value]="c">{{ c }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Urgency</mat-label>
            <mat-select formControlName="urgency">
              @for (u of urgencies; track u) {
                <mat-option [value]="u">{{ u }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Effectivity date</mat-label>
            <input matInput type="date" formControlName="effectivityDate" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Reason for change</mat-label>
            <input matInput maxlength="500" formControlName="reason" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Description</mat-label>
            <textarea matInput rows="2" formControlName="description"></textarea>
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Impact assessment</mat-label>
            <textarea matInput rows="2" formControlName="impactAssessment"></textarea>
          </mat-form-field>
          <div class="full">
            <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || busy()">
              <mat-icon>save</mat-icon> Save
            </button>
          </div>
        </form>
      } @else {
        @if (eco(); as e) {
          <dl class="facts">
            <dt>Category</dt>
            <dd>{{ e.changeCategory }}</dd>
            <dt>Urgency</dt>
            <dd>{{ e.urgency }}</dd>
            <dt>Effectivity</dt>
            <dd>{{ e.effectivityDate | date: 'dd MMM yyyy' }}</dd>
            <dt>Reason</dt>
            <dd>{{ e.reason }}</dd>
            @if (e.description) {
              <dt>Description</dt>
              <dd>{{ e.description }}</dd>
            }
            @if (e.impactAssessment) {
              <dt>Impact assessment</dt>
              <dd>{{ e.impactAssessment }}</dd>
            }
            @if (e.statusReason) {
              <dt>Last status reason</dt>
              <dd>{{ e.statusReason }}</dd>
            }
            @if (e.approvedAt) {
              <dt>Approved</dt>
              <dd>{{ e.approvedAt | date: 'dd MMM yyyy HH:mm' }}</dd>
            }
            @if (e.implementedAt) {
              <dt>Implemented</dt>
              <dd>{{ e.implementedAt | date: 'dd MMM yyyy HH:mm' }}</dd>
            }
          </dl>
        }
      }

      <h3 class="mat-h3">Affected items</h3>
      @if (editable()) {
        <form class="edit-form" [formGroup]="itemForm" (ngSubmit)="addItem()">
          <mat-form-field appearance="outline">
            <mat-label>Type</mat-label>
            <mat-select formControlName="itemType">
              @for (t of itemTypes; track t) {
                <mat-option [value]="t">{{ t }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="wide">
            <mat-label>Product</mat-label>
            <mat-select formControlName="productId">
              @for (p of productOptions(); track p.productId) {
                <mat-option [value]="p.productId">{{ p.productCode }} - {{ p.productName }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="wide">
            <mat-label>Change description</mat-label>
            <input matInput maxlength="300" formControlName="changeDescription" />
          </mat-form-field>
          <button mat-stroked-button type="submit" [disabled]="itemForm.invalid">
            <mat-icon>add</mat-icon> Add item
          </button>
        </form>
      }

      <table mat-table [dataSource]="items()" class="mat-elevation-z1">
        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">{{ r.itemType }}</td>
        </ng-container>
        <ng-container matColumnDef="product">
          <th mat-header-cell *matHeaderCellDef>Product</th>
          <td mat-cell *matCellDef="let r">{{ r.productLabel }}</td>
        </ng-container>
        <ng-container matColumnDef="change">
          <th mat-header-cell *matHeaderCellDef>Change</th>
          <td mat-cell *matCellDef="let r">{{ r.changeDescription }}</td>
        </ng-container>
        <ng-container matColumnDef="revision">
          <th mat-header-cell *matHeaderCellDef>BOM revision</th>
          <td mat-cell *matCellDef="let r">
            @if (r.newBomLabel) {
              {{ r.oldBomLabel }} &rarr; {{ r.newBomLabel }} (DRAFT)
            } @else {
              -
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="remove">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r; let i = index">
            @if (editable()) {
              <button mat-icon-button aria-label="Remove item" (click)="removeItem(i)">
                <mat-icon>delete</mat-icon>
              </button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="itemColumns()"></tr>
        <tr mat-row *matRowDef="let row; columns: itemColumns()"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="itemColumns().length" style="text-align:center;padding:1rem">
            No affected items
          </td>
        </tr>
      </table>

      @if (eco(); as e) {
        <div class="actions">
          @if (can('ECO_CREATE') && has('submit')) {
            <button mat-flat-button color="primary" [disabled]="dirty() || busy()" (click)="run('submit')">
              <mat-icon>send</mat-icon> Submit for review
            </button>
          }
          @if (can('ECO_APPROVE') && has('approve')) {
            <button mat-flat-button color="primary" [disabled]="busy()" (click)="run('approve')">
              <mat-icon>verified</mat-icon> Approve
            </button>
          }
          @if (can('ECO_APPROVE') && has('return')) {
            <button mat-stroked-button [disabled]="busy()" (click)="ask('return')">
              <mat-icon>undo</mat-icon> Return to draft
            </button>
          }
          @if (can('ECO_CREATE') && has('implement')) {
            <button mat-flat-button color="primary" [disabled]="busy()" (click)="run('implement')">
              <mat-icon>engineering</mat-icon> Implement (create draft BOM revisions)
            </button>
          }
          @if ((can('ECO_CREATE') || can('ECO_APPROVE')) && has('cancel')) {
            <button mat-stroked-button color="warn" [disabled]="busy()" (click)="ask('cancel')">
              <mat-icon>cancel</mat-icon> Cancel ECO
            </button>
          }
          @if (dirty() && has('submit')) {
            <span class="hint">Save your changes before submitting.</span>
          }
        </div>
        @if (pending(); as p) {
          <div class="actions">
            <mat-form-field appearance="outline" class="wide">
              <mat-label>Reason ({{ p === 'return' ? 'return to draft' : 'cancel' }})</mat-label>
              <input matInput maxlength="500" [formControl]="reasonCtrl" />
            </mat-form-field>
            <button mat-flat-button color="primary" [disabled]="!reasonCtrl.value.trim() || busy()" (click)="confirm()">
              Confirm
            </button>
            <button mat-button (click)="pending.set(null)">Dismiss</button>
          </div>
        }
      }
    }
    @if (eco(); as e) {
      <app-document-attachments entityType="ECO" [entityId]="e.id" />
    }
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .wide {
      min-width: 260px;
    }
    .full {
      width: 100%;
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
      margin: 16px 0;
    }
    .hint {
      opacity: 0.7;
    }
  `,
})
export class EcoDetailComponent implements OnInit {
  private api = inject(PlmApi);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private snack = inject(MatSnackBar);
  private session = inject(SessionStore);
  private fb = inject(FormBuilder);

  categories = ECO_CATEGORIES;
  urgencies = ECO_URGENCIES;
  itemTypes = ECO_ITEM_TYPES;

  eco = signal<EcoDto | null>(null);
  isNew = signal(false);
  loading = signal(false);
  busy = signal(false);
  items = signal<ItemRow[]>([]);
  itemsChanged = signal(false);
  productOptions = signal<ProductLifecycleRowDto[]>([]);
  pending = signal<'return' | 'cancel' | null>(null);
  reasonCtrl = new FormControl('', { nonNullable: true });

  editable = computed(() => this.isNew() || (this.eco()?.status === 'DRAFT' && this.can('ECO_CREATE')));
  itemColumns = computed(() =>
    this.editable() ? ['type', 'product', 'change', 'remove'] : ['type', 'product', 'change', 'revision'],
  );
  dirty = () => this.form.dirty || this.itemsChanged();

  form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    changeCategory: this.fb.nonNullable.control<EcoChangeCategory>('BOM_CHANGE'),
    urgency: this.fb.nonNullable.control<EcoUrgency>('NORMAL'),
    effectivityDate: [''],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
    description: [''],
    impactAssessment: [''],
  });
  itemForm = this.fb.group({
    itemType: this.fb.nonNullable.control<EcoItemType>('BOM'),
    productId: this.fb.control<number | null>(null, Validators.required),
    changeDescription: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(300)]),
  });

  can(code: string): boolean {
    return this.session.hasPermission(code);
  }

  has(action: string): boolean {
    return this.eco()?.allowedActions.includes(action) ?? false;
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id === 'new') {
      this.isNew.set(true);
      this.loadProducts();
    } else if (id) {
      this.load(Number(id));
    }
  }

  private load(id: number): void {
    this.loading.set(true);
    this.api.getEco(id).subscribe({
      next: (e) => {
        this.show(e);
        this.loading.set(false);
        if (this.editable()) this.loadProducts();
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load the ECO', 'Dismiss', { duration: 3000 });
      },
    });
  }

  private show(e: EcoDto): void {
    this.eco.set(e);
    this.isNew.set(false);
    this.form.reset({
      title: e.title,
      changeCategory: e.changeCategory,
      urgency: e.urgency,
      effectivityDate: e.effectivityDate ?? '',
      reason: e.reason,
      description: e.description ?? '',
      impactAssessment: e.impactAssessment ?? '',
    });
    this.items.set(
      e.items.map((i) => ({
        itemType: i.itemType,
        productId: i.productId,
        changeDescription: i.changeDescription,
        productLabel: `${i.productCode ?? i.productId} - ${i.productName ?? ''}`,
        oldBomLabel: i.oldBomLabel,
        newBomLabel: i.newBomLabel,
      })),
    );
    this.itemsChanged.set(false);
    this.pending.set(null);
  }

  private loadProducts(): void {
    this.api.listLifecycle(null, null, 0, 200).subscribe({
      next: (page) => this.productOptions.set(page.content),
      error: () => this.productOptions.set([]),
    });
  }

  addItem(): void {
    if (this.itemForm.invalid) return;
    const v = this.itemForm.getRawValue();
    const productId = v.productId as number;
    if (this.items().some((i) => i.itemType === v.itemType && i.productId === productId)) {
      this.snack.open('That product is already listed for this item type', 'Dismiss', { duration: 3000 });
      return;
    }
    const p = this.productOptions().find((o) => o.productId === productId);
    this.items.update((list) => [
      ...list,
      {
        itemType: v.itemType,
        productId,
        changeDescription: v.changeDescription.trim(),
        productLabel: p ? `${p.productCode} - ${p.productName}` : String(productId),
        oldBomLabel: null,
        newBomLabel: null,
      },
    ]);
    this.itemsChanged.set(true);
    this.itemForm.reset({ itemType: 'BOM', productId: null, changeDescription: '' });
  }

  removeItem(index: number): void {
    this.items.update((list) => list.filter((_, i) => i !== index));
    this.itemsChanged.set(true);
  }

  save(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    const req = {
      title: v.title.trim(),
      description: v.description.trim() || null,
      reason: v.reason.trim(),
      changeCategory: v.changeCategory,
      urgency: v.urgency,
      effectivityDate: v.effectivityDate || null,
      impactAssessment: v.impactAssessment.trim() || null,
      items: this.items().map((i) => ({
        itemType: i.itemType,
        productId: i.productId,
        changeDescription: i.changeDescription,
      })),
    };
    const current = this.eco();
    this.busy.set(true);
    const call = current ? this.api.updateEco(current.id, req) : this.api.createEco(req);
    call.subscribe({
      next: (e) => {
        this.busy.set(false);
        this.snack.open('ECO saved', 'Dismiss', { duration: 2500 });
        if (current) {
          this.show(e);
        } else {
          this.router.navigate(['/plm/ecos', e.id]);
        }
      },
      error: (err) => this.fail(err, 'Could not save the ECO'),
    });
  }

  run(action: 'submit' | 'approve' | 'implement'): void {
    const e = this.eco();
    if (!e) return;
    const calls: Record<string, Observable<EcoDto>> = {
      submit: this.api.submitEco(e.id),
      approve: this.api.approveEco(e.id),
      implement: this.api.implementEco(e.id),
    };
    this.busy.set(true);
    calls[action].subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.show(updated);
      },
      error: (err) => this.fail(err, `Could not ${action} the ECO`),
    });
  }

  ask(kind: 'return' | 'cancel'): void {
    this.reasonCtrl.reset('');
    this.pending.set(kind);
  }

  confirm(): void {
    const e = this.eco();
    const kind = this.pending();
    const reason = this.reasonCtrl.value.trim();
    if (!e || !kind || !reason) return;
    this.busy.set(true);
    const call = kind === 'return' ? this.api.returnEco(e.id, reason) : this.api.cancelEco(e.id, reason);
    call.subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.show(updated);
      },
      error: (err) => this.fail(err, 'Could not change the ECO status'),
    });
  }

  private fail(err: { error?: { detail?: string } }, fallback: string): void {
    this.busy.set(false);
    this.snack.open(err?.error?.detail ?? fallback, 'Dismiss', { duration: 5000 });
  }
}
