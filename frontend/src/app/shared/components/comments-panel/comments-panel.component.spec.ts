import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { CommentsPanelComponent } from './comments-panel.component';

describe('CommentsPanelComponent', () => {
  let fixture: ComponentFixture<CommentsPanelComponent>;
  let component: CommentsPanelComponent;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CommentsPanelComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(CommentsPanelComponent);
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

    httpMock.expectNone((r) => r.url.includes('/api/comments'));

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No comments yet.');
  });

  it('should fetch comments when entityType and entityId are set', () => {
    fixture.componentRef.setInput('entityType', 'MATERIAL');
    fixture.componentRef.setInput('entityId', 10);
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/comments?entityType=MATERIAL&entityId=10');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should render fetched comments', () => {
    fixture.componentRef.setInput('entityType', 'SUPPLIER');
    fixture.componentRef.setInput('entityId', 3);
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/comments?entityType=SUPPLIER&entityId=3');
    req.flush([
      {
        id: 1,
        entityType: 'SUPPLIER',
        entityId: 3,
        parentId: null,
        body: 'Test comment',
        createdAt: '2026-01-01T10:00:00Z',
        createdBy: 42,
        editedAt: null,
        deleted: false,
      },
    ]);

    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Test comment');
  });
});
