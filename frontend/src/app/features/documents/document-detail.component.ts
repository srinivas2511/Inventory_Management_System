import { Component, OnInit, inject, signal } from '@angular/core';

import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { DocumentDto, DocumentVersionDto, DocumentsApi, LINKABLE_TYPES, formatBytes, saveBlob } from './documents.api';

type ReasonAction = 'reject' | 'obsolete';

@Component({
  selector: 'app-document-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    IstDatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <a routerLink="/documents" class="back"><mat-icon>arrow_back</mat-icon> Documents</a>

    @if (doc(); as d) {
      <div class="page-header">
        <h2 class="mat-h2">
          {{ d.documentNumber }} — {{ d.title }}
          <app-status-badge [status]="d.status" />
        </h2>
      </div>
      <p class="meta">
        {{ d.categoryName }} · version {{ d.currentVersionNo }} · updated
        {{ d.updatedAt | istDate: 'dd MMM yyyy HH:mm' }}
      </p>
      @if (d.description) {
        <p>{{ d.description }}</p>
      }
      @if (d.statusReason) {
        <p class="reason" data-testid="status-reason"><mat-icon>info</mat-icon> {{ d.statusReason }}</p>
      }

      <div class="actions">
        <button mat-stroked-button type="button" (click)="download()" data-testid="download-current">
          <mat-icon>download</mat-icon> Download current
        </button>
        @if (d.status === 'DRAFT') {
          <button
            *appHasPermission="'DOCUMENT_UPLOAD'"
            mat-flat-button
            color="primary"
            type="button"
            (click)="run(api.submit(d.id))"
            data-testid="submit"
          >
            Submit for review
          </button>
        }
        @if (d.status === 'REVIEW') {
          <button
            *appHasPermission="'DOCUMENT_APPROVE'"
            mat-flat-button
            color="primary"
            type="button"
            (click)="run(api.approve(d.id))"
            data-testid="approve"
          >
            Approve
          </button>
          <button
            *appHasPermission="'DOCUMENT_APPROVE'"
            mat-stroked-button
            type="button"
            (click)="askReason('reject')"
            data-testid="reject"
          >
            Return to draft
          </button>
        }
        @if (d.status === 'DRAFT' || d.status === 'APPROVED') {
          <button
            *appHasPermission="'DOCUMENT_APPROVE'"
            mat-stroked-button
            color="warn"
            type="button"
            (click)="askReason('obsolete')"
            data-testid="obsolete"
          >
            Mark obsolete
          </button>
        }
      </div>

      @if (pending(); as action) {
        <form class="reason-form" (ngSubmit)="confirmReason()">
          <mat-form-field appearance="outline" class="grow">
            <mat-label>Reason ({{ action === 'reject' ? 'return to draft' : 'obsolete' }})</mat-label>
            <input matInput [formControl]="reasonCtrl" maxlength="500" data-testid="reason" />
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit" [disabled]="reasonCtrl.invalid">Confirm</button>
          <button mat-button type="button" (click)="pending.set(null)">Cancel</button>
        </form>
      }

      <h3 class="mat-h3">Versions</h3>
      <table mat-table [dataSource]="d.versions" class="mat-elevation-z1">
        <ng-container matColumnDef="version">
          <th mat-header-cell *matHeaderCellDef>Version</th>
          <td mat-cell *matCellDef="let v">
            v{{ v.versionNo }}{{ v.revisionLabel ? ' (rev ' + v.revisionLabel + ')' : '' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="file">
          <th mat-header-cell *matHeaderCellDef>File</th>
          <td mat-cell *matCellDef="let v">{{ v.originalFilename }} · {{ size(v) }}</td>
        </ng-container>
        <ng-container matColumnDef="note">
          <th mat-header-cell *matHeaderCellDef>Change note</th>
          <td mat-cell *matCellDef="let v">{{ v.changeNote }}</td>
        </ng-container>
        <ng-container matColumnDef="uploaded">
          <th mat-header-cell *matHeaderCellDef>Uploaded</th>
          <td mat-cell *matCellDef="let v">{{ v.uploadedAt | istDate: 'dd MMM yyyy HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="checksum">
          <th mat-header-cell *matHeaderCellDef>SHA-256</th>
          <td mat-cell *matCellDef="let v" class="mono" [title]="v.sha256">{{ v.sha256.substring(0, 12) }}…</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let v">
            <button mat-icon-button type="button" aria-label="Download this version" (click)="download(v.versionNo)">
              <mat-icon>download</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="versionColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: versionColumns"></tr>
      </table>

      @if (d.status !== 'OBSOLETE') {
        <form
          *appHasPermission="'DOCUMENT_UPLOAD'"
          [formGroup]="versionForm"
          (ngSubmit)="addVersion()"
          class="inline-form"
        >
          <mat-form-field appearance="outline">
            <mat-label>Revision label</mat-label>
            <input matInput formControlName="revisionLabel" maxlength="20" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="grow">
            <mat-label>Change note</mat-label>
            <input matInput formControlName="changeNote" maxlength="500" />
          </mat-form-field>
          <input #picker type="file" hidden (change)="onVersionFile($event)" data-testid="version-file" />
          <button mat-stroked-button type="button" (click)="picker.click()">
            <mat-icon>attach_file</mat-icon> {{ versionFile()?.name ?? 'Choose file' }}
          </button>
          <button mat-flat-button color="primary" type="submit" [disabled]="!versionFile()" data-testid="add-version">
            <mat-icon>upload</mat-icon> Upload new version
          </button>
        </form>
      }

      <h3 class="mat-h3">Linked records</h3>
      @if (d.links.length === 0) {
        <p class="hint">Not linked to any record.</p>
      }
      <ul class="links">
        @for (l of d.links; track l.id) {
          <li>
            {{ l.entityType }} #{{ l.entityId }}
            <button
              *appHasPermission="'DOCUMENT_UPLOAD'"
              mat-icon-button
              type="button"
              aria-label="Remove link"
              (click)="run(api.removeLink(d.id, l.id))"
            >
              <mat-icon>link_off</mat-icon>
            </button>
          </li>
        }
      </ul>
      <form *appHasPermission="'DOCUMENT_UPLOAD'" [formGroup]="linkForm" (ngSubmit)="addLink()" class="inline-form">
        <mat-form-field appearance="outline">
          <mat-label>Record type</mat-label>
          <mat-select formControlName="entityType">
            @for (t of linkTypes; track t) {
              <mat-option [value]="t">{{ t }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Record ID</mat-label>
          <input matInput type="number" formControlName="entityId" data-testid="link-entity-id" />
        </mat-form-field>
        <button mat-stroked-button type="submit" [disabled]="linkForm.invalid" data-testid="add-link">
          <mat-icon>add_link</mat-icon> Link
        </button>
      </form>
    } @else if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    }
  `,
  styles: `
    .back {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      text-decoration: none;
    }
    .meta,
    .hint {
      color: var(--mat-sys-on-surface-variant, #666);
    }
    .reason {
      display: flex;
      gap: 6px;
      align-items: center;
    }
    .actions,
    .inline-form,
    .reason-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .grow {
      flex: 1 1 260px;
    }
    .mono {
      font-family: monospace;
    }
    .links {
      list-style: none;
      padding: 0;
    }
  `,
})
export class DocumentDetailComponent implements OnInit {
  protected readonly api = inject(DocumentsApi);
  private readonly route = inject(ActivatedRoute);
  private readonly snack = inject(MatSnackBar);

  readonly linkTypes = LINKABLE_TYPES;
  readonly versionColumns = ['version', 'file', 'note', 'uploaded', 'checksum', 'actions'];
  readonly doc = signal<DocumentDto | null>(null);
  readonly loading = signal(true);
  readonly pending = signal<ReasonAction | null>(null);
  readonly versionFile = signal<File | null>(null);

  readonly reasonCtrl = new FormControl<string>('', { nonNullable: true, validators: [Validators.required] });
  readonly versionForm = new FormGroup({
    revisionLabel: new FormControl<string>('', { nonNullable: true }),
    changeNote: new FormControl<string>('', { nonNullable: true }),
  });
  readonly linkForm = new FormGroup({
    entityType: new FormControl<string>('PRODUCT', { nonNullable: true, validators: [Validators.required] }),
    entityId: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(1)] }),
  });

  size(v: DocumentVersionDto): string {
    return formatBytes(v.sizeBytes);
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.get(id).subscribe({
      next: (d) => {
        this.doc.set(d);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Could not load the document', 'Close', { duration: 5000 });
      },
    });
  }

  /** Runs a state change or link change and shows the document it returns. */
  run(call: Observable<DocumentDto>): void {
    call.subscribe({
      next: (d) => {
        this.doc.set(d);
        this.pending.set(null);
        this.reasonCtrl.reset('');
      },
      error: (e) => this.snack.open(e?.error?.detail ?? 'The change was refused', 'Close', { duration: 6000 }),
    });
  }

  askReason(action: ReasonAction): void {
    this.reasonCtrl.reset('');
    this.pending.set(action);
  }

  confirmReason(): void {
    const d = this.doc();
    const action = this.pending();
    if (!d || !action || this.reasonCtrl.invalid) return;
    const reason = this.reasonCtrl.value.trim();
    this.run(action === 'reject' ? this.api.reject(d.id, reason) : this.api.obsolete(d.id, reason));
  }

  download(version?: number): void {
    const d = this.doc();
    if (!d) return;
    const v = d.versions.find((x) => x.versionNo === (version ?? d.currentVersionNo));
    this.api.download(d.id, version).subscribe({
      next: (f) => saveBlob(f.blob, f.filename ?? v?.originalFilename ?? d.documentNumber),
      error: () => this.snack.open('Download failed', 'Close', { duration: 5000 }),
    });
  }

  onVersionFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.versionFile.set(input.files && input.files.length > 0 ? input.files[0] : null);
  }

  addVersion(): void {
    const d = this.doc();
    const file = this.versionFile();
    if (!d || !file) return;
    const v = this.versionForm.getRawValue();
    this.run(
      this.api.addVersion(
        d.id,
        { revisionLabel: v.revisionLabel.trim() || null, changeNote: v.changeNote.trim() || null },
        file,
      ),
    );
    this.versionFile.set(null);
    this.versionForm.reset({ revisionLabel: '', changeNote: '' });
  }

  addLink(): void {
    const d = this.doc();
    const v = this.linkForm.getRawValue();
    if (!d || this.linkForm.invalid || v.entityId == null) return;
    this.run(this.api.addLink(d.id, v.entityType, v.entityId));
    this.linkForm.patchValue({ entityId: null });
  }
}
