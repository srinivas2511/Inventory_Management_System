import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { NotificationInboxComponent } from './notification-inbox.component';
import { NotificationDto, Page, notificationLink } from './notifications.api';

function note(id: number, overrides: Partial<NotificationDto> = {}): NotificationDto {
  return {
    id,
    templateCode: 'LOW_STOCK',
    subject: `[MEDIUM] Low stock ${id}`,
    body: 'Spring wire 2.0 mm is below its reorder level',
    entityType: 'ALERT',
    entityId: 40 + id,
    createdAt: '2026-10-04T08:00:00Z',
    readAt: null,
    read: false,
    ...overrides,
  };
}

const pageOf = (items: NotificationDto[]): Page<NotificationDto> => ({
  content: items,
  totalElements: items.length,
  totalPages: 1,
  size: 50,
  number: 0,
});

describe('NotificationInboxComponent', () => {
  let fixture: ComponentFixture<NotificationInboxComponent>;
  let backend: HttpTestingController;

  function setup(items: NotificationDto[]): void {
    fixture = TestBed.createComponent(NotificationInboxComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const req = backend.expectOne((r) => r.url === '/api/notifications');
    expect(req.request.params.get('unreadOnly')).toBe('false');
    req.flush(pageOf(items));
    fixture.detectChanges();
  }

  const rows = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('[data-testid="row"]'));

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationInboxComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists the notifications, unread ones emphasised', () => {
    setup([note(1), note(2, { read: true, readAt: '2026-10-04T09:00:00Z' })]);
    expect(rows().length).toBe(2);
    expect(rows()[0].classList.contains('unread')).toBe(true);
    expect(rows()[1].classList.contains('unread')).toBe(false);
    expect(rows()[0].textContent).toContain('[MEDIUM] Low stock 1');
    expect(rows()[0].textContent).toContain('below its reorder level');
  });

  it('shows an empty state', () => {
    setup([]);
    expect(fixture.nativeElement.querySelector('[data-testid="empty"]')).not.toBeNull();
  });

  it('offers "mark read" only on unread rows and updates the row in place', () => {
    setup([note(1), note(2, { read: true, readAt: 'x' })]);
    expect(fixture.nativeElement.querySelectorAll('[data-testid="read"]').length).toBe(1);
    (fixture.nativeElement.querySelector('[data-testid="read"]') as HTMLButtonElement).click();
    backend.expectOne('/api/notifications/1/read').flush(note(1, { read: true, readAt: 'now' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('[data-testid="read"]').length).toBe(0);
    expect(rows()[0].classList.contains('unread')).toBe(false);
  });

  it('reloads with the unread-only filter', () => {
    setup([note(1)]);
    fixture.componentInstance.setUnreadOnly(true);
    const req = backend.expectOne((r) => r.url === '/api/notifications');
    expect(req.request.params.get('unreadOnly')).toBe('true');
    req.flush(pageOf([]));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="empty"]')).not.toBeNull();
  });

  it('drops a row from the unread-only view once it is read', () => {
    setup([]);
    fixture.componentInstance.setUnreadOnly(true);
    backend.expectOne((r) => r.url === '/api/notifications').flush(pageOf([note(1), note(2)]));
    fixture.componentInstance.read(note(1));
    backend.expectOne('/api/notifications/1/read').flush(note(1, { read: true }));
    expect(fixture.componentInstance.items().map((n) => n.id)).toEqual([2]);
  });

  it('marks everything read and reloads', () => {
    setup([note(1), note(2)]);
    fixture.componentInstance.markAll();
    backend.expectOne('/api/notifications/read-all').flush({ unread: 0 });
    backend
      .expectOne((r) => r.url === '/api/notifications')
      .flush(pageOf([note(1, { read: true }), note(2, { read: true })]));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('[data-testid="read"]').length).toBe(0);
  });

  it('opens the record a notification is about and marks it read', () => {
    setup([note(1, { entityType: 'CAPA', entityId: 3 })]);
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture.componentInstance.open(fixture.componentInstance.items()[0]);
    backend.expectOne('/api/notifications/1/read').flush(note(1, { read: true }));
    expect(navigate).toHaveBeenCalledWith(['/quality/capa', '3']);
  });
});

describe('notificationLink', () => {
  it('maps record types to screens', () => {
    expect(notificationLink({ entityType: 'CAPA', entityId: 8 })).toEqual(['/quality/capa', '8']);
    expect(notificationLink({ entityType: 'ALERT', entityId: 8 })).toEqual(['/insight/alerts']);
    expect(notificationLink({ entityType: 'PURCHASE_ORDER', entityId: 8 })).toEqual(['/procurement/purchase-orders']);
  });

  it('has no link for unknown or missing records', () => {
    expect(notificationLink({ entityType: null, entityId: null })).toBeNull();
    expect(notificationLink({ entityType: 'WIDGET', entityId: 1 })).toBeNull();
    expect(notificationLink({ entityType: 'CAPA', entityId: null })).toBeNull();
  });
});
