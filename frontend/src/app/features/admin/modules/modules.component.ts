import { Component, OnInit, inject } from '@angular/core';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { ModuleDto, ModuleStore } from '../../../core/modules/module.store';

/** Switch the optional modules on or off. A module that is off disappears from the navigation and its API refuses. */
@Component({
  selector: 'app-modules',
  standalone: true,
  imports: [MatTableModule, MatSlideToggleModule, MatSnackBarModule],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Modules</h2>
    </div>
    <p>
      A module that is switched off disappears from the menu and its screens stop working. Stock movements keep being
      journalled in Finance either way, so switching Finance off hides its screens but never loses an entry.
    </p>
    <table mat-table [dataSource]="store.modules()" class="mat-elevation-z1">
      <ng-container matColumnDef="name">
        <th mat-header-cell *matHeaderCellDef>Module</th>
        <td mat-cell *matCellDef="let m">{{ m.name }}</td>
      </ng-container>
      <ng-container matColumnDef="active">
        <th mat-header-cell *matHeaderCellDef>Active</th>
        <td mat-cell *matCellDef="let m">
          <mat-slide-toggle
            [checked]="m.active"
            [attr.data-testid]="'module-' + m.code"
            [attr.aria-label]="(m.active ? 'Switch off ' : 'Switch on ') + m.name"
            (change)="toggle(m, $event.checked)"
          />
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table>
  `,
})
export class ModulesComponent implements OnInit {
  protected readonly store = inject(ModuleStore);
  private readonly snack = inject(MatSnackBar);
  protected readonly columns = ['name', 'active'];

  ngOnInit(): void {
    this.store.load();
  }

  protected toggle(module: ModuleDto, active: boolean): void {
    this.store.setActive(module.code, active).subscribe({
      next: () =>
        this.snack.open(`${module.name} ${active ? 'switched on' : 'switched off'}`, 'OK', { duration: 3000 }),
      error: () => this.store.load(),
    });
  }
}
