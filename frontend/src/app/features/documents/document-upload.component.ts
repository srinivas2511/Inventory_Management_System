import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CreateDocumentRequest, DocumentCategoryDto, DocumentsApi, LINKABLE_TYPES, formatBytes } from './documents.api';

@Component({
  selector: 'app-document-upload',
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
  ],
  template: `
    <a routerLink="/documents" class="back"><mat-icon>arrow_back</mat-icon> Documents</a>
    <h2 class="mat-h2">Upload document</h2>

    <form [formGroup]="form" (ngSubmit)="submit()" class="grid">
      <mat-form-field appearance="outline">
        <mat-label>Category</mat-label>
        <mat-select formControlName="categoryCode" data-testid="category">
          @for (c of categories(); track c.categoryCode) {
            <mat-option [value]="c.categoryCode">{{ c.name }}</mat-option>
          }
        </mat-select>
        @if (selectedCategory()?.requiredAuthority; as auth) {
          <mat-hint>Needs the {{ auth }} permission</mat-hint>
        }
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Title</mat-label>
        <input matInput formControlName="title" maxlength="200" data-testid="title" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Description</mat-label>
        <textarea matInput formControlName="description" rows="2" maxlength="500"></textarea>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Revision label</mat-label>
        <input matInput formControlName="revisionLabel" maxlength="20" placeholder="e.g. A, B, 1.0" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Change note</mat-label>
        <input matInput formControlName="changeNote" maxlength="500" />
      </mat-form-field>

      <div class="wide link-row">
        <mat-form-field appearance="outline">
          <mat-label>Link to record type</mat-label>
          <mat-select formControlName="linkType">
            <mat-option [value]="null">No link</mat-option>
            @for (t of linkTypes; track t) {
              <mat-option [value]="t">{{ t }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        @if (form.controls.linkType.value) {
          <mat-form-field appearance="outline">
            <mat-label>Record ID</mat-label>
            <input matInput type="number" formControlName="linkId" data-testid="link-id" />
          </mat-form-field>
        }
      </div>

      <div class="wide file-row">
        <input #picker type="file" hidden (change)="onFile($event)" data-testid="file-input" />
        <button mat-stroked-button type="button" (click)="picker.click()">
          <mat-icon>attach_file</mat-icon> Choose file
        </button>
        @if (file(); as f) {
          <span class="file-name" data-testid="file-name">{{ f.name }} ({{ size(f) }})</span>
        } @else {
          <span class="hint">No file chosen</span>
        }
      </div>

      <div class="wide actions">
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || !file() || saving()">
          <mat-icon>upload</mat-icon> Upload
        </button>
      </div>
    </form>
  `,
  styles: `
    .back {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      text-decoration: none;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 12px 16px;
      max-width: 900px;
    }
    .wide {
      grid-column: 1 / -1;
    }
    .link-row,
    .file-row {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
    }
    .hint {
      color: var(--mat-sys-on-surface-variant, #666);
    }
  `,
})
export class DocumentUploadComponent implements OnInit {
  private readonly api = inject(DocumentsApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snack = inject(MatSnackBar);

  readonly linkTypes = LINKABLE_TYPES;
  readonly categories = signal<DocumentCategoryDto[]>([]);
  readonly file = signal<File | null>(null);
  readonly saving = signal(false);

  readonly form = new FormGroup({
    categoryCode: new FormControl<string>('', { nonNullable: true, validators: [Validators.required] }),
    title: new FormControl<string>('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
    description: new FormControl<string>('', { nonNullable: true }),
    revisionLabel: new FormControl<string>('', { nonNullable: true }),
    changeNote: new FormControl<string>('', { nonNullable: true }),
    linkType: new FormControl<string | null>(null),
    linkId: new FormControl<number | null>(null),
  });

  selectedCategory(): DocumentCategoryDto | undefined {
    return this.categories().find((c) => c.categoryCode === this.form.controls.categoryCode.value);
  }

  size(f: File): string {
    return formatBytes(f.size);
  }

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    const type = q.get('entityType');
    const id = q.get('entityId');
    if (type && id) {
      this.form.patchValue({ linkType: type, linkId: Number(id) });
    }
    this.api.categories().subscribe({
      next: (cs) => {
        this.categories.set(cs.filter((c) => c.active));
        const preset = q.get('category');
        if (preset && cs.some((c) => c.categoryCode === preset)) {
          this.form.patchValue({ categoryCode: preset });
        }
      },
      error: () => this.snack.open('Could not load categories', 'Close', { duration: 4000 }),
    });
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files && input.files.length > 0 ? input.files[0] : null);
  }

  submit(): void {
    const file = this.file();
    if (this.form.invalid || !file) return;
    const v = this.form.getRawValue();
    const req: CreateDocumentRequest = {
      categoryCode: v.categoryCode,
      title: v.title.trim(),
      description: v.description.trim() || null,
      revisionLabel: v.revisionLabel.trim() || null,
      changeNote: v.changeNote.trim() || null,
      links: v.linkType && v.linkId != null ? [{ entityType: v.linkType, entityId: v.linkId }] : [],
    };
    this.saving.set(true);
    this.api.create(req, file).subscribe({
      next: (d) => {
        this.snack.open(`Uploaded ${d.documentNumber}`, 'Close', { duration: 3000 });
        void this.router.navigate(['/documents', d.id]);
      },
      error: (e) => {
        this.saving.set(false);
        this.snack.open(e?.error?.detail ?? 'Upload failed', 'Close', { duration: 6000 });
      },
    });
  }
}
