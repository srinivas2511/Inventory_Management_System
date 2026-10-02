import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { DatePipe } from '@angular/common';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { AppConfigService } from '../../../core/config/app-config.service';

interface AuditLogDto { id: number; occurredAt: string; username: string; action: string; entity: string; entityId: string; reason?: string; }
interface Page<T> { content: T[]; totalElements: number; }

@Component({
  selector: 'app-audit-log-list',
  standalone: true,
  imports: [ReactiveFormsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule, DatePipe],
  template: `
    <div style="padding:16px 16px 0"><h2 class="mat-h2">Audit Logs</h2></div>
    <div style="display:flex;gap:12px;padding:8px 16px;flex-wrap:wrap">
      <mat-form-field appearance="outline" style="flex:1;min-width:200px">
        <mat-label>Filter by entity</mat-label>
        <input matInput [formControl]="entityCtrl" />
      </mat-form-field>
      <mat-form-field appearance="outline" style="flex:1;min-width:200px">
        <mat-label>Filter by action</mat-label>
        <input matInput [formControl]="actionCtrl" />
      </mat-form-field>
    </div>
    @if (loading()) { <div style="display:flex;justify-content:center;padding:48px"><mat-spinner diameter="40"/></div> } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1" style="width:100%">
        <ng-container matColumnDef="occurredAt"><th mat-header-cell *matHeaderCellDef>When</th><td mat-cell *matCellDef="let r">{{ r.occurredAt | date:'short' }}</td></ng-container>
        <ng-container matColumnDef="username"><th mat-header-cell *matHeaderCellDef>User</th><td mat-cell *matCellDef="let r">{{ r.username }}</td></ng-container>
        <ng-container matColumnDef="action"><th mat-header-cell *matHeaderCellDef>Action</th><td mat-cell *matCellDef="let r">{{ r.action }}</td></ng-container>
        <ng-container matColumnDef="entity"><th mat-header-cell *matHeaderCellDef>Entity</th><td mat-cell *matCellDef="let r">{{ r.entity }}</td></ng-container>
        <ng-container matColumnDef="entityId"><th mat-header-cell *matHeaderCellDef>ID</th><td mat-cell *matCellDef="let r">{{ r.entityId }}</td></ng-container>
        <tr mat-header-row *matHeaderRowDef="['occurredAt','username','action','entity','entityId']"></tr>
        <tr mat-row *matRowDef="let row; columns: ['occurredAt','username','action','entity','entityId'];"></tr>
      </table>
      <mat-paginator [length]="total()" [pageSize]="pageSize" [pageIndex]="pageIndex()"
        [pageSizeOptions]="[20,50]" (page)="onPage($event)" showFirstLastButtons />
    }
  `,
})
export class AuditLogListComponent implements OnInit {
  protected readonly rows = signal<AuditLogDto[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = 20;
  protected readonly entityCtrl = new FormControl('');
  protected readonly actionCtrl = new FormControl('');
  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;

  ngOnInit(): void {
    this.load();
    this.entityCtrl.valueChanges.pipe(debounceTime(400), distinctUntilChanged()).subscribe(() => { this.pageIndex.set(0); this.load(); });
    this.actionCtrl.valueChanges.pipe(debounceTime(400), distinctUntilChanged()).subscribe(() => { this.pageIndex.set(0); this.load(); });
  }

  protected load(): void {
    this.loading.set(true);
    let params = new HttpParams().set('page', this.pageIndex()).set('size', this.pageSize);
    if (this.entityCtrl.value) params = params.set('entity', this.entityCtrl.value);
    if (this.actionCtrl.value) params = params.set('action', this.actionCtrl.value);
    this.http.get<Page<AuditLogDto>>(`${this.base}/api/audit-logs`, { params }).subscribe({
      next: (p) => { this.rows.set(p.content); this.total.set(p.totalElements); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  protected onPage(e: PageEvent): void { this.pageIndex.set(e.pageIndex); this.load(); }
}
