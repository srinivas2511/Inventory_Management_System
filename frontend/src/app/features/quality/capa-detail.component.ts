import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { CAPA_STATUSES, Capa, CapaStatus, QualityPlusApi } from './quality-plus.api';
import { DocumentAttachmentsComponent } from '../documents/document-attachments.component';

type NewSource = 'INTERNAL_AUDIT' | 'REJECTION_ANALYSIS';

@Component({
  selector: 'app-capa-detail',
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
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <div>
        <a routerLink="/quality/capa" class="back"><mat-icon>arrow_back</mat-icon> CAPA</a>
        <h2 class="mat-h2">
          @if (capa(); as c) {
            {{ c.capaNumber }} <app-status-badge [status]="c.status" />
          } @else {
            New CAPA
          }
        </h2>
      </div>
    </div>

    @if (isNew()) {
      <form [formGroup]="newForm" (ngSubmit)="createNew()" class="grid">
        <mat-form-field appearance="outline">
          <mat-label>Raised from</mat-label>
          <mat-select formControlName="source">
            <mat-option value="INTERNAL_AUDIT">Internal audit</mat-option>
            <mat-option value="REJECTION_ANALYSIS">Rejection</mat-option>
          </mat-select>
        </mat-form-field>
        @if (newForm.controls.source.value === 'REJECTION_ANALYSIS') {
          <mat-form-field appearance="outline">
            <mat-label>Rejection ID</mat-label>
            <input matInput type="number" formControlName="rejectionId" />
            <mat-hint>The title and description are filled in from the rejection</mat-hint>
          </mat-form-field>
        } @else {
          <mat-form-field appearance="outline" class="full">
            <mat-label>Title</mat-label>
            <input matInput formControlName="title" maxlength="200" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Description</mat-label>
            <textarea matInput rows="3" formControlName="description"></textarea>
          </mat-form-field>
        }
        <div class="full">
          <button mat-flat-button color="primary" type="submit" [disabled]="busy()">Create CAPA</button>
        </div>
      </form>
    } @else {
      @if (capa(); as c) {
        <ol class="steps">
          @for (s of statuses; track s) {
            <li [class.current]="s === c.status" [class.done]="isDone(c.status, s)">{{ s }}</li>
          }
        </ol>
        <p class="meta">
          Source: {{ c.sourceType }}{{ c.sourceLabel ? ' - ' + c.sourceLabel : '' }}. Opened
          {{ c.openedAt | date: 'dd MMM yyyy' }}
          @if (c.closedAt) {
            , closed {{ c.closedAt | date: 'dd MMM yyyy' }}
          }
          .
        </p>

        <form [formGroup]="form" (ngSubmit)="save()" class="grid">
          <mat-form-field appearance="outline" class="full">
            <mat-label>Title</mat-label>
            <input matInput formControlName="title" maxlength="200" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Description</mat-label>
            <textarea matInput rows="3" formControlName="description"></textarea>
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Root cause</mat-label>
            <textarea matInput rows="3" formControlName="rootCause"></textarea>
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Corrective action</mat-label>
            <textarea matInput rows="3" formControlName="correctiveAction"></textarea>
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Preventive action</mat-label>
            <textarea matInput rows="3" formControlName="preventiveAction"></textarea>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Target completion date</mat-label>
            <input matInput type="date" formControlName="targetCompletionDate" />
          </mat-form-field>
          @if (c.status !== 'CLOSED') {
            <div class="full">
              <button
                *appHasPermission="'CAPA_MANAGE'"
                mat-flat-button
                color="primary"
                type="submit"
                [disabled]="busy()"
              >
                Save
              </button>
            </div>
          }
        </form>

        @if (c.status !== 'CLOSED') {
          <section *appHasPermission="'CAPA_MANAGE'" class="panel">
            <h3 class="mat-h3">Move forward</h3>
            @if (c.status === 'VERIFICATION') {
              <mat-form-field appearance="outline" class="full">
                <mat-label>Reason (required to send back to action)</mat-label>
                <input matInput [formControl]="reason" maxlength="500" />
              </mat-form-field>
            }
            <div class="row">
              @for (t of forwardTargets(); track t) {
                <button mat-stroked-button (click)="advance(t)" [disabled]="busy()">Move to {{ t }}</button>
              }
            </div>
          </section>
        }

        @if (c.status === 'VERIFICATION') {
          <section *appHasPermission="'CAPA_CLOSE'" class="panel">
            <h3 class="mat-h3">Verify and close</h3>
            <mat-form-field appearance="outline" class="full">
              <mat-label>Verification notes - why the fix is effective</mat-label>
              <textarea matInput rows="3" [formControl]="verification"></textarea>
            </mat-form-field>
            <button mat-flat-button color="primary" (click)="close()" [disabled]="busy() || verification.invalid">
              Close CAPA
            </button>
          </section>
        }

        @if (c.status === 'CLOSED' && c.verificationNotes) {
          <section class="panel">
            <h3 class="mat-h3">Verification</h3>
            <p>{{ c.verificationNotes }}</p>
          </section>
        }
      } @else {
        <div class="loading-center"><mat-spinner diameter="40" /></div>
      }
    }
    @if (capa(); as c) {
      <app-document-attachments entityType="CAPA" [entityId]="c.id" defaultCategory="CAPA_EVIDENCE" />
    }
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
    }
    .back {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      text-decoration: none;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 4px 16px;
      max-width: 900px;
    }
    .full {
      grid-column: 1 / -1;
    }
    .steps {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      list-style: none;
      padding: 0;
      margin: 8px 0;
    }
    .steps li {
      padding: 4px 12px;
      border-radius: 14px;
      border: 1px solid #bbb;
      font-size: 12px;
      opacity: 0.6;
    }
    .steps li.done {
      opacity: 1;
      border-color: #2e7d32;
    }
    .steps li.current {
      opacity: 1;
      background: #1565c0;
      border-color: #1565c0;
      color: #fff;
    }
    .meta {
      margin: 0 0 12px;
    }
    .panel {
      margin: 20px 0;
      max-width: 900px;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
  `,
})
export class CapaDetailComponent implements OnInit {
  private api = inject(QualityPlusApi);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private snack = inject(MatSnackBar);

  statuses = CAPA_STATUSES;
  capa = signal<Capa | null>(null);
  busy = signal(false);
  isNew = signal(false);

  newForm = new FormGroup({
    source: new FormControl<NewSource>('INTERNAL_AUDIT', { nonNullable: true }),
    title: new FormControl('', { nonNullable: true }),
    description: new FormControl('', { nonNullable: true }),
    rejectionId: new FormControl<number | null>(null),
  });
  form = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
    description: new FormControl('', { nonNullable: true }),
    rootCause: new FormControl('', { nonNullable: true }),
    correctiveAction: new FormControl('', { nonNullable: true }),
    preventiveAction: new FormControl('', { nonNullable: true }),
    targetCompletionDate: new FormControl('', { nonNullable: true }),
  });
  reason = new FormControl('', { nonNullable: true });
  verification = new FormControl('', { nonNullable: true, validators: Validators.required });

  /** Closing has its own action, so the move-forward buttons never offer CLOSED. */
  forwardTargets = computed<CapaStatus[]>(() => (this.capa()?.nextStatuses ?? []).filter((s) => s !== 'CLOSED'));

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id || id === 'new') {
      this.isNew.set(true);
    } else {
      this.load(Number(id));
    }
  }

  isDone(current: CapaStatus, step: CapaStatus): boolean {
    return CAPA_STATUSES.indexOf(step) < CAPA_STATUSES.indexOf(current);
  }

  private show(c: Capa): void {
    this.capa.set(c);
    this.form.patchValue({
      title: c.title,
      description: c.description ?? '',
      rootCause: c.rootCause ?? '',
      correctiveAction: c.correctiveAction ?? '',
      preventiveAction: c.preventiveAction ?? '',
      targetCompletionDate: c.targetCompletionDate ?? '',
    });
  }

  private load(id: number): void {
    this.api.getCapa(id).subscribe({
      next: (c) => this.show(c),
      error: (err) => this.fail(err, 'Failed to load the CAPA'),
    });
  }

  createNew(): void {
    const v = this.newForm.getRawValue();
    this.busy.set(true);
    if (v.source === 'REJECTION_ANALYSIS') {
      if (!v.rejectionId) {
        this.busy.set(false);
        this.snack.open('Enter the rejection ID', 'Dismiss', { duration: 3000 });
        return;
      }
      this.api.capaFromRejection(v.rejectionId).subscribe({
        next: (c) => this.created(c),
        error: (err) => this.fail(err, 'Failed to raise the CAPA'),
      });
      return;
    }
    if (!v.title.trim()) {
      this.busy.set(false);
      this.snack.open('Enter a title', 'Dismiss', { duration: 3000 });
      return;
    }
    this.api
      .createCapa({
        title: v.title.trim(),
        description: v.description || null,
        sourceType: 'INTERNAL_AUDIT',
        sourceId: null,
        rootCause: null,
        correctiveAction: null,
        preventiveAction: null,
        targetCompletionDate: null,
        assignedTo: null,
      })
      .subscribe({
        next: (c) => this.created(c),
        error: (err) => this.fail(err, 'Failed to raise the CAPA'),
      });
  }

  private created(c: Capa): void {
    this.busy.set(false);
    this.router.navigate(['/quality/capa', c.id]);
  }

  save(): void {
    const c = this.capa();
    if (!c || this.form.invalid) return;
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.api
      .updateCapa(c.id, {
        title: v.title.trim(),
        description: v.description || null,
        sourceType: c.sourceType,
        sourceId: c.sourceId,
        rootCause: v.rootCause || null,
        correctiveAction: v.correctiveAction || null,
        preventiveAction: v.preventiveAction || null,
        targetCompletionDate: v.targetCompletionDate || null,
        assignedTo: c.assignedTo,
      })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.show(updated);
          this.snack.open('CAPA saved', 'Dismiss', { duration: 3000 });
        },
        error: (err) => this.fail(err, 'Failed to save the CAPA'),
      });
  }

  advance(target: CapaStatus): void {
    const c = this.capa();
    if (!c) return;
    this.busy.set(true);
    this.api.advanceCapa(c.id, target, this.reason.value.trim() || null).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.reason.reset('');
        this.show(updated);
      },
      error: (err) => this.fail(err, 'Failed to move the CAPA'),
    });
  }

  close(): void {
    const c = this.capa();
    if (!c || this.verification.invalid) return;
    this.busy.set(true);
    this.api.closeCapa(c.id, this.verification.value.trim()).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.show(updated);
      },
      error: (err) => this.fail(err, 'Failed to close the CAPA'),
    });
  }

  private fail(err: { error?: { detail?: string } }, fallback: string): void {
    this.busy.set(false);
    this.snack.open(err?.error?.detail ?? fallback, 'Dismiss', { duration: 5000 });
  }
}
