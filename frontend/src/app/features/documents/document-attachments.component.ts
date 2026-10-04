import { Component, Input, OnChanges, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { SessionStore } from '../../core/auth/session.store';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { DocumentCategoryDto, DocumentSummaryDto, DocumentsApi, formatBytes, saveBlob } from './documents.api';

/**
 * Documents attached to one record (a product, BOM, ECO, CAPA ...): lists them, downloads the current version and,
 * for people who may upload, attaches a new one in place. Drop it into any detail screen.
 */
@Component({
  selector: 'app-document-attachments',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <section *appHasPermission="'DOCUMENT_VIEW'" class="attachments" data-testid="attachments">
      <h3 class="mat-h3"><mat-icon>attach_file</mat-icon> Documents</h3>
      @if (documents().length === 0) {
        <p class="hint" data-testid="no-documents">No documents attached.</p>
      }
      <ul class="docs">
        @for (d of documents(); track d.id) {
          <li>
            <a [routerLink]="['/documents', d.id]">{{ d.documentNumber }}</a>
            <span class="title">{{ d.title }}</span>
            <span class="hint">{{ d.categoryName }} · v{{ d.currentVersionNo }}</span>
            <app-status-badge [status]="d.status" />
            <button mat-icon-button type="button" aria-label="Download" (click)="download(d)">
              <mat-icon>download</mat-icon>
            </button>
          </li>
        }
      </ul>

      <ng-container *appHasPermission="'DOCUMENT_UPLOAD'">
        @if (!attaching()) {
          <button mat-stroked-button type="button" (click)="open()" data-testid="attach">
            <mat-icon>add</mat-icon> Attach document
          </button>
        } @else {
          <form [formGroup]="form" (ngSubmit)="attach()" class="attach-form">
            <mat-form-field appearance="outline">
              <mat-label>Category</mat-label>
              <mat-select formControlName="categoryCode">
                @for (c of categories(); track c.categoryCode) {
                  <mat-option [value]="c.categoryCode">{{ c.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline" class="grow">
              <mat-label>Title</mat-label>
              <input matInput formControlName="title" maxlength="200" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Revision</mat-label>
              <input matInput formControlName="revisionLabel" maxlength="20" />
            </mat-form-field>
            <input #picker type="file" hidden (change)="onFile($event)" data-testid="attach-file" />
            <button mat-stroked-button type="button" (click)="picker.click()">
              <mat-icon>attach_file</mat-icon> {{ file()?.name ?? 'Choose file' }}
            </button>
            @if (file(); as f) {
              <span class="hint">{{ size(f) }}</span>
            }
            <button
              mat-flat-button
              color="primary"
              type="submit"
              [disabled]="form.invalid || !file()"
              data-testid="attach-save"
            >
              Attach
            </button>
            <button mat-button type="button" (click)="attaching.set(false)">Cancel</button>
          </form>
        }
      </ng-container>
    </section>
  `,
  styles: `
    .attachments {
      margin-top: 16px;
    }
    .docs {
      list-style: none;
      padding: 0;
    }
    .docs li {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 4px 0;
    }
    .title {
      font-weight: 500;
    }
    .hint {
      color: var(--mat-sys-on-surface-variant, #666);
    }
    .attach-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding-top: 8px;
    }
    .grow {
      flex: 1 1 240px;
    }
  `,
})
export class DocumentAttachmentsComponent implements OnChanges {
  private readonly api = inject(DocumentsApi);
  private readonly snack = inject(MatSnackBar);
  private readonly session = inject(SessionStore);

  @Input({ required: true }) entityType!: string;
  @Input({ required: true }) entityId!: number;
  /** Category preselected when attaching, e.g. CAPA_EVIDENCE on a CAPA. */
  @Input() defaultCategory: string | null = null;

  readonly documents = signal<DocumentSummaryDto[]>([]);
  readonly categories = signal<DocumentCategoryDto[]>([]);
  readonly attaching = signal(false);
  readonly file = signal<File | null>(null);

  readonly form = new FormGroup({
    categoryCode: new FormControl<string>('', { nonNullable: true, validators: [Validators.required] }),
    title: new FormControl<string>('', { nonNullable: true, validators: [Validators.required] }),
    revisionLabel: new FormControl<string>('', { nonNullable: true }),
  });

  size(f: File): string {
    return formatBytes(f.size);
  }

  ngOnChanges(): void {
    // someone who cannot read documents never sees the section, so do not ask the server for them
    if (this.entityType && this.entityId != null && this.session.hasPermission('DOCUMENT_VIEW')) {
      this.refresh();
    }
  }

  refresh(): void {
    this.api.list({ entityType: this.entityType, entityId: this.entityId, size: 100 }).subscribe({
      next: (p) => this.documents.set(p.content),
      // a user without DOCUMENT_VIEW never sees the section, so a failure here is not worth interrupting for
      error: () => this.documents.set([]),
    });
  }

  open(): void {
    this.attaching.set(true);
    if (this.categories().length === 0) {
      this.api.categories().subscribe({
        next: (cs) => {
          this.categories.set(cs.filter((c) => c.active));
          if (this.defaultCategory && cs.some((c) => c.categoryCode === this.defaultCategory)) {
            this.form.patchValue({ categoryCode: this.defaultCategory });
          }
        },
        error: () => this.snack.open('Could not load categories', 'Close', { duration: 4000 }),
      });
    }
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files && input.files.length > 0 ? input.files[0] : null);
  }

  attach(): void {
    const file = this.file();
    if (this.form.invalid || !file) return;
    const v = this.form.getRawValue();
    this.api
      .create(
        {
          categoryCode: v.categoryCode,
          title: v.title.trim(),
          revisionLabel: v.revisionLabel.trim() || null,
          links: [{ entityType: this.entityType, entityId: this.entityId }],
        },
        file,
      )
      .subscribe({
        next: () => {
          this.attaching.set(false);
          this.file.set(null);
          this.form.reset({ categoryCode: this.defaultCategory ?? '', title: '', revisionLabel: '' });
          this.refresh();
        },
        error: (e) => this.snack.open(e?.error?.detail ?? 'Could not attach the document', 'Close', { duration: 6000 }),
      });
  }

  download(d: DocumentSummaryDto): void {
    this.api.download(d.id).subscribe({
      next: (f) => saveBlob(f.blob, f.filename ?? d.documentNumber),
      error: () => this.snack.open('Download failed', 'Close', { duration: 5000 }),
    });
  }
}
