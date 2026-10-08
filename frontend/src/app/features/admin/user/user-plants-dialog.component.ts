import { Component, OnInit, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule, MatSelectionListChange } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';
import { problemMessage } from '../../../core/models/problem.model';
import { UserDto } from '../admin.api';
import { PlantApi, PlantDto, UserPlantsDto } from '../plants/plant.api';

export interface UserPlantsData {
  user: UserDto;
}

/** Limits a user to some plants. Choosing none lifts every restriction: the user sees all plants. */
@Component({
  selector: 'app-user-plants-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatListModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Plants for {{ data.user.fullName }}</h2>
    <mat-dialog-content>
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="32" /></div>
      } @else {
        @if (allAccess()) {
          <p class="mat-body-2 note" data-testid="plants-all-access">
            A role of {{ data.user.username }} grants access to all plants, so these choices have no effect until that
            role is removed.
          </p>
        }
        <mat-selection-list (selectionChange)="onSelection($event)" aria-label="Plants" data-testid="plants-list">
          @for (p of plants(); track p.id) {
            <mat-list-option [value]="p.id" [selected]="selected.includes(p.id)">
              <span matListItemTitle>{{ p.plantCode }} · {{ p.name }}</span>
            </mat-list-option>
          }
        </mat-selection-list>
        <p class="mat-body-2 note">
          Choose none to let {{ data.user.username }} see every plant. If they are signed in, the change applies on
          their next request.
        </p>
      }
      @if (errorMessage()) {
        <p class="form-error mat-body-2" role="alert" data-testid="plants-error">{{ errorMessage() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        color="primary"
        (click)="save()"
        [disabled]="saving() || loading()"
        data-testid="plants-save"
      >
        Save plants
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .loading {
        display: flex;
        justify-content: center;
        padding: 24px;
      }
      .note {
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
    `,
  ],
})
export class UserPlantsDialogComponent implements OnInit {
  protected readonly data = inject<UserPlantsData>(MAT_DIALOG_DATA);
  protected readonly plants = signal<PlantDto[]>([]);
  protected readonly allAccess = signal(false);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected selected: number[] = [];

  private readonly api = inject(PlantApi);
  private readonly dialogRef = inject(MatDialogRef<UserPlantsDialogComponent, UserPlantsDto>);

  ngOnInit(): void {
    forkJoin({ plants: this.api.list(), assigned: this.api.userPlants(this.data.user.id) }).subscribe({
      next: ({ plants, assigned }) => {
        this.plants.set(plants.filter((p) => p.active || assigned.plantIds.includes(p.id)));
        this.selected = [...assigned.plantIds];
        this.allAccess.set(assigned.allAccess);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(problemMessage(err, 'The plants could not be loaded.'));
      },
    });
  }

  protected onSelection(change: MatSelectionListChange): void {
    this.selected = change.source.selectedOptions.selected.map((o) => o.value as number);
  }

  protected save(): void {
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.assignUserPlants(this.data.user.id, this.selected).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.dialogRef.close(saved);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The plants could not be saved.'));
      },
    });
  }
}
