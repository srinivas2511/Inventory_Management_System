import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatListModule } from '@angular/material/list';
import { IstDatePipe } from '../../pipes/ist-date.pipe';

export interface CommentDto {
  id: number;
  entityType: string;
  entityId: number;
  parentId: number | null;
  body: string;
  createdAt: string;
  createdBy: number | null;
  editedAt: string | null;
  deleted: boolean;
}

@Component({
  selector: 'app-comments-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatChipsModule,
    MatListModule,
    IstDatePipe,
  ],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title>Comments</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        @if (comments().length === 0) {
          <p class="empty-hint" data-testid="empty-hint">No comments yet.</p>
        }

        <mat-list>
          @for (c of comments(); track c.id) {
            <mat-list-item>
              <div class="comment-block">
                <span class="comment-meta">
                  User #{{ c.createdBy }} &middot; {{ c.createdAt | istDate: 'short' }}
                </span>
                <span class="comment-text" [class.deleted-comment]="c.deleted">{{ c.body }}</span>
              </div>
            </mat-list-item>
          }
        </mat-list>

        <div class="add-comment">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Add a comment</mat-label>
            <textarea
              matInput
              [(ngModel)]="newBody"
              rows="2"
              data-testid="comment-input"
            ></textarea>
          </mat-form-field>
          <div class="comment-actions">
            <button
              mat-flat-button
              color="primary"
              (click)="addComment()"
              [disabled]="!newBody.trim()"
              data-testid="add-comment-btn"
            >
              Add
            </button>
          </div>
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
      .comment-block {
        display: flex;
        flex-direction: column;
        padding: 4px 0;
        width: 100%;
      }
      .comment-meta {
        font-size: 0.75rem;
        color: rgba(0, 0, 0, 0.54);
      }
      .comment-text {
        margin-top: 2px;
      }
      .deleted-comment {
        text-decoration: line-through;
        opacity: 0.5;
      }
      .add-comment {
        margin-top: 12px;
      }
      .full-width {
        width: 100%;
      }
      .comment-actions {
        display: flex;
        justify-content: flex-end;
      }
    `,
  ],
})
export class CommentsPanelComponent implements OnInit {
  @Input() entityType = '';
  @Input() entityId = 0;

  protected comments = signal<CommentDto[]>([]);
  protected newBody = '';

  private readonly http = inject(HttpClient);

  ngOnInit(): void {
    if (this.entityId > 0 && this.entityType) {
      this.loadComments();
    }
  }

  private loadComments(): void {
    this.http
      .get<CommentDto[]>(
        `/api/comments?entityType=${this.entityType}&entityId=${this.entityId}`,
      )
      .subscribe({
        next: (list) => this.comments.set(list),
        // eslint-disable-next-line @typescript-eslint/no-empty-function
        error: () => {},
      });
  }

  protected addComment(): void {
    const body = this.newBody.trim();
    if (!body || !this.entityType || !this.entityId) return;

    this.http
      .post<CommentDto>('/api/comments', {
        entityType: this.entityType,
        entityId: this.entityId,
        body,
      })
      .subscribe({
        next: () => {
          this.newBody = '';
          this.loadComments();
        },
        // eslint-disable-next-line @typescript-eslint/no-empty-function
        error: () => {},
      });
  }
}
