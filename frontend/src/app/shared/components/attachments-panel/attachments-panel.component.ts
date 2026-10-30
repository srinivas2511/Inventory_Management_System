import { Component, ElementRef, Input, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';

export interface AttachmentDto {
  id: number;
  entityType: string;
  entityId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
  createdBy: number | null;
}

@Component({
  selector: 'app-attachments-panel',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatListModule, MatIconModule],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title>Attachments</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        @if (attachments().length === 0) {
          <p class="empty-hint" data-testid="empty-hint">No attachments yet.</p>
        }

        <mat-list>
          @for (a of attachments(); track a.id) {
            <mat-list-item>
              <mat-icon matListItemIcon>attach_file</mat-icon>
              <div matListItemTitle class="file-name">{{ a.fileName }}</div>
              <div matListItemLine class="file-meta">
                {{ formatSize(a.sizeBytes) }}
              </div>
              <button
                mat-icon-button
                [attr.aria-label]="'Download ' + a.fileName"
                (click)="download(a)"
                class="download-btn"
              >
                <mat-icon>download</mat-icon>
              </button>
            </mat-list-item>
          }
        </mat-list>

        <div class="upload-row">
          <input #fileInput type="file" hidden (change)="onFileSelected($event)" data-testid="file-input" />
          <button
            mat-flat-button
            color="primary"
            (click)="fileInput.click()"
            [disabled]="uploading()"
            data-testid="upload-btn"
            aria-label="Upload attachment"
          >
            <mat-icon aria-hidden="true">upload</mat-icon>
            {{ uploading() ? 'Uploading…' : 'Upload' }}
          </button>
        </div>
      </mat-card-content>
    </mat-card>
  `,
  styles: [
    `
      .empty-hint {
        color: rgba(0, 0, 0, 0.54);
        margin: 8px 0;
      }
      .file-name {
        font-weight: 500;
      }
      .file-meta {
        font-size: 0.75rem;
        color: rgba(0, 0, 0, 0.54);
      }
      .upload-row {
        margin-top: 12px;
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .download-btn {
        margin-left: auto;
      }
    `,
  ],
})
export class AttachmentsPanelComponent implements OnInit {
  @Input() entityType = '';
  @Input() entityId = 0;

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  protected attachments = signal<AttachmentDto[]>([]);
  protected uploading = signal(false);

  private readonly http = inject(HttpClient);

  ngOnInit(): void {
    if (this.entityId > 0 && this.entityType) {
      this.loadAttachments();
    }
  }

  private loadAttachments(): void {
    this.http
      .get<AttachmentDto[]>(`/api/attachments?entityType=${this.entityType}&entityId=${this.entityId}`)
      .subscribe({
        next: (list) => this.attachments.set(list),
        // eslint-disable-next-line @typescript-eslint/no-empty-function
        error: () => {},
      });
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !this.entityType || !this.entityId) return;

    this.uploading.set(true);
    const fd = new FormData();
    fd.append('entityType', this.entityType);
    fd.append('entityId', String(this.entityId));
    fd.append('file', file);

    this.http.post<AttachmentDto>('/api/attachments', fd).subscribe({
      next: () => {
        this.uploading.set(false);
        // Reset file input so the same file can be re-uploaded
        input.value = '';
        this.loadAttachments();
      },
      error: () => {
        this.uploading.set(false);
      },
    });
  }

  protected download(a: AttachmentDto): void {
    window.open(`/api/attachments/${a.id}/download`, '_blank');
  }

  protected formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
}
