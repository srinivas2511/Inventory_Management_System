import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';

import { PagedList } from '../paged-list';

/** The paginator every server-paged list screen shows under its table. */
@Component({
  selector: 'app-list-paginator',
  standalone: true,
  imports: [MatPaginatorModule],
  template: `
    <mat-paginator
      [length]="paged.total()"
      [pageIndex]="paged.pageIndex()"
      [pageSize]="paged.pageSize()"
      [pageSizeOptions]="[20, 50, 100]"
      showFirstLastButtons
      (page)="onPage($event)"
    />
  `,
})
export class ListPaginatorComponent {
  @Input({ required: true }) paged!: PagedList;
  @Output() changed = new EventEmitter<void>();

  protected onPage(event: PageEvent): void {
    if (this.paged.change(event)) this.changed.emit();
  }
}
