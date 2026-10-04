import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { NotificationBellComponent } from './notification-bell.component';
import { NotificationDto, Page } from './notifications.api';

function note(overrides: Partial<NotificationDto> = {}): NotificationDto {
  return {
    id: 1,
    templateCode: 'CAPA_ASSIGNED',
    subject: 'CAPA CAPA/26/00003 assigned to you',
    body: 'CAPA "Burr on coil ends" has been assigned to you.',
    entityType: 'CAPA',
    entityId: 3,
    createdAt: '2026-10-04T08:00:00Z',
    readAt: null,
    read: false,
    ...overrides,
  };
}

function pageOf(items: NotificationDto[]): Page<NotificationDto> {
  return { content: items, totalElements: items.length, totalPages: 1, size: 8, number: 0 };
}

describe('NotificationBellComponent', () => {
  let fixture: ComponentFixture<NotificationBellComponent>;
  let backend: HttpTestingController;

  function setup(unread: number): void {
    fixture = TestBed.createComponent(NotificationBellComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/notifications/unread-count').flush({ unread });
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationBellComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('shows the unread count on the bell', () => {
    setup(3);
    expect(fixture.componentInstance.unread()).toBe(3);
    const badge = fixture.nativeElement.querySelector('.mat-badge-content') as HTMLElement;
    expect(badge.textContent!.trim()).toBe('3');
    expect((fixture.nativeElement.querySelector('mat-icon') as HTMLElement).classList).not.toContain(
      'mat-badge-hidden',
    );
  });

  it('hides the badge when everything has been read', () => {
    setup(0);
    expect((fixture.nativeElement.querySelector('mat-icon') as HTMLElement).classList).toContain('mat-badge-hidden');
  });

  it('loads the latest notifications when the menu opens', () => {
    setup(1);
    (fixture.nativeElement.querySelector('[data-testid="bell"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const req = backend.expectOne((r) => r.url === '/api/notifications');
    expect(req.request.params.get('size')).toBe('8');
    req.flush(pageOf([note()]));
    fixture.detectChanges();
    const items = document.querySelectorAll('[data-testid="bell-item"]');
    expect(items.length).toBe(1);
    expect(items[0].textContent).toContain('CAPA CAPA/26/00003 assigned to you');
  });

  it('says when there is nothing to show', () => {
    setup(0);
    (fixture.nativeElement.querySelector('[data-testid="bell"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/notifications').flush(pageOf([]));
    fixture.detectChanges();
    expect(document.querySelector('[data-testid="bell-empty"]')).not.toBeNull();
  });

  it('opens an unread notification: read first, then count, then navigate', () => {
    setup(1);
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);

    fixture.componentInstance.open(note());
    backend.expectOne('/api/notifications/1/read').flush(note({ read: true }));
    backend.expectOne('/api/notifications/unread-count').flush({ unread: 0 });

    expect(fixture.componentInstance.unread()).toBe(0);
    expect(navigate).toHaveBeenCalledWith(['/quality/capa', '3']);
  });

  it('goes straight to the record for a notification that is already read', () => {
    setup(0);
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture.componentInstance.open(note({ read: true, readAt: '2026-10-04T09:00:00Z' }));
    expect(navigate).toHaveBeenCalledWith(['/quality/capa', '3']);
  });

  it('still navigates when marking read fails', () => {
    setup(1);
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture.componentInstance.open(note());
    backend.expectOne('/api/notifications/1/read').flush({}, { status: 500, statusText: 'Server error' });
    expect(navigate).toHaveBeenCalled();
  });

  it('marks everything read', () => {
    setup(4);
    fixture.componentInstance.recent.set([note(), note({ id: 2 })]);
    fixture.componentInstance.markAll();
    backend.expectOne('/api/notifications/read-all').flush({ unread: 0 });
    expect(fixture.componentInstance.unread()).toBe(0);
    expect(fixture.componentInstance.recent().every((n) => n.read)).toBe(true);
  });

  it('keeps the old count when the server cannot be reached', () => {
    setup(2);
    fixture.componentInstance.refreshCount();
    backend.expectOne('/api/notifications/unread-count').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(fixture.componentInstance.unread()).toBe(2);
  });
});
