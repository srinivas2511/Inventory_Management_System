/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { PeriodListComponent } from './period-list.component';

const periods = [
  {
    id: 3,
    periodName: 'APR-2026',
    fiscalYear: 2026,
    periodNumber: 1,
    startDate: '2026-04-01',
    endDate: '2026-04-30',
    status: 'CLOSING',
  },
  {
    id: 4,
    periodName: 'MAY-2026',
    fiscalYear: 2026,
    periodNumber: 2,
    startDate: '2026-05-01',
    endDate: '2026-05-31',
    status: 'CLOSED',
  },
];

describe('PeriodListComponent reasons (DESIGN §15.6)', () => {
  let fixture: ComponentFixture<PeriodListComponent>;
  let backend: HttpTestingController;
  let instance: Record<string, any>;

  function dialogReturning(value: unknown) {
    return spyOn(instance['dialog'], 'open').and.returnValue({
      afterClosed: () => of(value),
    } as unknown as MatDialogRef<unknown>);
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PeriodListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(PeriodListComponent);
    backend = TestBed.inject(HttpTestingController);
    instance = fixture.componentInstance as unknown as Record<string, any>;
    fixture.detectChanges();
    backend.expectOne('/api/periods').flush(periods);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('finalizing a close sends the reason the user gave', () => {
    const open = dialogReturning('April review signed off');

    instance['finalizeClose'](periods[0]);

    const post = backend.expectOne('/api/periods/3/finalize-close');
    expect(post.request.body).toEqual({ reason: 'April review signed off' });
    expect((open.calls.mostRecent().args as any[])[1].data.confirmLabel).toBe('Close period');
    post.flush({ ...periods[0], status: 'CLOSED' });
    backend.expectOne('/api/periods').flush(periods);
  });

  it('reopening sends the reason too', () => {
    dialogReturning('Late supplier invoice');

    instance['reopen'](periods[1]);

    const post = backend.expectOne('/api/periods/4/reopen');
    expect(post.request.body).toEqual({ reason: 'Late supplier invoice' });
    post.flush({ ...periods[1], status: 'OPEN' });
    backend.expectOne('/api/periods').flush(periods);
  });

  it('sends nothing when the reason dialog is dismissed', () => {
    dialogReturning(undefined);

    instance['finalizeClose'](periods[0]);
    instance['reopen'](periods[1]);

    backend.expectNone('/api/periods/3/finalize-close');
    backend.expectNone('/api/periods/4/reopen');
  });
});
