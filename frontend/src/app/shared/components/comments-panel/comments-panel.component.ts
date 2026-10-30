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
import { MatTabsModule } from '@angular/material/tabs';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
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

export interface ReminderDto {
  id: number;
  entityType: string;
  entityId: number;
  remindAt: string;
  userId: number;
  note: string | null;
  dismissed: boolean;
  createdAt: string;
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
    MatTabsModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    IstDatePipe,
  ],
  template: `
    <mat-card>
      <mat-card-content>
        <mat-tab-group>
          <mat-tab label="Comments">
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
          </mat-tab>

          <mat-tab label="Reminders">
            @if (reminders().length === 0) {
              <p class="empty-hint">No reminders set.</p>
            }

            <mat-list>
              @for (r of reminders(); track r.id) {
                <mat-list-item>
                  <div class="comment-block">
                    <span class="comment-meta">
                      {{ r.remindAt | istDate: 'short' }}
                      @if (r.dismissed) { <span class="dismissed-badge">Dismissed</span> }
                    </span>
                    <span class="comment-text">{{ r.note }}</span>
                    @if (!r.dismissed) {
                      <button mat-icon-button aria-label="Dismiss reminder" (click)="dismissReminder(r)">
                        <mat-icon>check_circle</mat-icon>
                      </button>
                    }
                  </div>
                </mat-list-item>
              }
            </mat-list>

            <div class="add-comment">
              <mat-form-field appearance="outline">
                <mat-label>Remind at</mat-label>
                <input matInput [matDatepicker]="picker" [(ngModel)]="newReminderDate" />
                <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
                <mat-datepicker #picker></mat-datepicker>
              </mat-form-field>
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Note</mat-label>
                <textarea matInput [(ngModel)]="newReminderNote" rows="2"></textarea>
              </mat-form-field>
              <div class="comment-actions">
                <button
                  mat-flat-button
                  color="primary"
                  (click)="addReminder()"
                  [disabled]="!newReminderDate"
                >
                  Add Reminder
                </button>
              </div>
            </div>
          </mat-tab>
        </mat-tab-group>
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
      .dismissed-badge {
        background: #e0e0e0;
        border-radius: 4px;
        padding: 0 4px;
        font-size: 0.7rem;
        margin-left: 4px;
      }
    `,
  ],
})
export class CommentsPanelComponent implements OnInit {
  @Input() entityType = '';
  @Input() entityId = 0;

  protected comments = signal<CommentDto[]>([]);
  protected reminders = signal<ReminderDto[]>([]);
  protected newBody = '';
  protected newReminderDate: Date | null = null;
  protected newReminderNote = '';

  private readonly http = inject(HttpClient);

  ngOnInit(): void {
    if (this.entityId > 0 && this.entityType) {
      this.loadComments();
      this.loadReminders();
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

  private loadReminders(): void {
    this.http
      .get<ReminderDto[]>(
        `/api/reminders?entityType=${this.entityType}&entityId=${this.entityId}`,
      )
      .subscribe({
        next: (list) => this.reminders.set(list),
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

  protected addReminder(): void {
    if (!this.newReminderDate || !this.entityType || !this.entityId) return;
    this.http
      .post<ReminderDto>('/api/reminders', {
        entityType: this.entityType,
        entityId: this.entityId,
        remindAt: this.newReminderDate.toISOString(),
        note: this.newReminderNote || null,
      })
      .subscribe({
        next: () => {
          this.newReminderDate = null;
          this.newReminderNote = '';
          this.loadReminders();
        },
        // eslint-disable-next-line @typescript-eslint/no-empty-function
        error: () => {},
      });
  }

  protected dismissReminder(reminder: ReminderDto): void {
    this.http
      .put<ReminderDto>(`/api/reminders/${reminder.id}/dismiss`, {})
      .subscribe({
        next: () => this.loadReminders(),
        // eslint-disable-next-line @typescript-eslint/no-empty-function
        error: () => {},
      });
  }
}
