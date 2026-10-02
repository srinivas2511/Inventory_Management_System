import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { AppConfigService } from '../../../core/config/app-config.service';
import { StatusBadgeComponent } from '../../../shared/components/status-badge.component';

interface UserDto {
  id: number; username: string; fullName: string; email: string;
  roles: string[]; active: boolean; lastLoginAt?: string;
}
interface Page<T> { content: T[]; totalElements: number; }

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [ReactiveFormsModule, MatTableModule, MatPaginatorModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatProgressSpinnerModule, StatusBadgeComponent],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Users</h2>
    </div>
    <div class="filter-bar">
      <mat-form-field appearance="outline" class="search-field">
        <mat-label>Search</mat-label>
        <input matInput [formControl]="searchCtrl" /><mat-icon matSuffix>search</mat-icon>
      </mat-form-field>
      <mat-form-field appearance="outline" style="width:140px">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="activeCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option [value]="true">Active</mat-option>
          <mat-option [value]="false">Inactive</mat-option>
        </mat-select>
      </mat-form-field>
    </div>
    @if (loading()) { <div class="loading-center"><mat-spinner diameter="40" /></div> } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="username"><th mat-header-cell *matHeaderCellDef>Username</th><td mat-cell *matCellDef="let r">{{ r.username }}</td></ng-container>
        <ng-container matColumnDef="fullName"><th mat-header-cell *matHeaderCellDef>Full name</th><td mat-cell *matCellDef="let r">{{ r.fullName }}</td></ng-container>
        <ng-container matColumnDef="email"><th mat-header-cell *matHeaderCellDef>Email</th><td mat-cell *matCellDef="let r">{{ r.email }}</td></ng-container>
        <ng-container matColumnDef="roles"><th mat-header-cell *matHeaderCellDef>Roles</th><td mat-cell *matCellDef="let r">{{ r.roles?.join(', ') }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.active ? 'ACTIVE' : 'INACTIVE'" /></td></ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns;"></tr>
      </table>
      <mat-paginator [length]="total()" [pageSize]="pageSize" [pageIndex]="pageIndex()"
        [pageSizeOptions]="[20,50]" (page)="onPage($event)" showFirstLastButtons />
    }
  `,
  styles: [`.page-header{display:flex;justify-content:space-between;align-items:center;padding:16px 16px 0}
    .filter-bar{display:flex;gap:12px;padding:8px 16px;flex-wrap:wrap}.search-field{flex:1;min-width:200px}
    .loading-center{display:flex;justify-content:center;padding:48px}table{width:100%}`],
})
export class UserListComponent implements OnInit {
  protected readonly columns = ['username', 'fullName', 'email', 'roles', 'status'];
  protected readonly rows = signal<UserDto[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = 20;
  protected readonly searchCtrl = new FormControl('');
  protected readonly activeCtrl = new FormControl<boolean | null>(true);
  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;

  ngOnInit(): void {
    this.load();
    this.searchCtrl.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => { this.pageIndex.set(0); this.load(); });
    this.activeCtrl.valueChanges.subscribe(() => { this.pageIndex.set(0); this.load(); });
  }

  protected load(): void {
    this.loading.set(true);
    let params = new HttpParams().set('page', this.pageIndex()).set('size', this.pageSize);
    if (this.searchCtrl.value) params = params.set('q', this.searchCtrl.value);
    if (this.activeCtrl.value !== null) params = params.set('active', this.activeCtrl.value!);
    this.http.get<Page<UserDto>>(`${this.base}/api/admin/users`, { params }).subscribe({
      next: (p) => { this.rows.set(p.content); this.total.set(p.totalElements); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  protected onPage(e: PageEvent): void { this.pageIndex.set(e.pageIndex); this.load(); }
}
