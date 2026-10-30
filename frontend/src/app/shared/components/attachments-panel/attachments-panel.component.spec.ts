import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { AttachmentsPanelComponent } from './attachments-panel.component';

describe('AttachmentsPanelComponent', () => {
  let fixture: ComponentFixture<AttachmentsPanelComponent>;
  let component: AttachmentsPanelComponent;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AttachmentsPanelComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(AttachmentsPanelComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should show empty state when no entityId', () => {
    fixture.componentRef.setInput('entityType', 'MATERIAL');
    fixture.componentRef.setInput('entityId', 0);
    fixture.detectChanges();

    httpMock.expectNone((r) => r.url.includes('/api/attachments'));

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No attachments yet.');
  });

  it('should fetch attachments when entityType and entityId are set', () => {
    fixture.componentRef.setInput('entityType', 'MATERIAL');
    fixture.componentRef.setInput('entityId', 7);
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/attachments?entityType=MATERIAL&entityId=7');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should render fetched attachments', () => {
    fixture.componentRef.setInput('entityType', 'SUPPLIER');
    fixture.componentRef.setInput('entityId', 2);
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/attachments?entityType=SUPPLIER&entityId=2');
    req.flush([
      {
        id: 1,
        entityType: 'SUPPLIER',
        entityId: 2,
        fileName: 'cert.pdf',
        contentType: 'application/pdf',
        sizeBytes: 2048,
        createdAt: '2026-01-01T10:00:00Z',
        createdBy: 1,
      },
    ]);

    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('cert.pdf');
    expect(el.textContent).toContain('2.0 KB');
  });

  it('should format file sizes correctly', () => {
    fixture.detectChanges();
    // Access protected method via cast
    const comp = component as unknown as { formatSize: (n: number) => string };
    expect(comp.formatSize(500)).toBe('500 B');
    expect(comp.formatSize(1536)).toBe('1.5 KB');
    expect(comp.formatSize(2 * 1024 * 1024)).toBe('2.0 MB');
  });
});
