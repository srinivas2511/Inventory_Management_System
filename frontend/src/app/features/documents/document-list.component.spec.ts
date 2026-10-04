import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { DocumentListComponent } from './document-list.component';
import { DocumentCategoryDto, DocumentSummaryDto, Page } from './documents.api';

const doc: DocumentSummaryDto = {
  id: 4,
  documentNumber: 'DOC/26/00004',
  title: 'Coiling SOP',
  categoryCode: 'SOP',
  categoryName: 'Standard operating procedure',
  status: 'APPROVED',
  currentVersionNo: 3,
  updatedAt: '2026-10-04T08:00:00Z',
  links: [{ id: 1, entityType: 'PRODUCT', entityId: 12 }],
};
const page: Page<DocumentSummaryDto> = { content: [doc], totalElements: 1, totalPages: 1, size: 50, number: 0 };
const categories: DocumentCategoryDto[] = [
  {
    id: 1,
    categoryCode: 'SOP',
    name: 'Standard operating procedure',
    retentionYears: 5,
    requiredAuthority: null,
    active: true,
  },
];

describe('DocumentListComponent', () => {
  let fixture: ComponentFixture<DocumentListComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'u',
      fullName: 'U',
      email: 'u@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(DocumentListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/document-categories').flush(categories);
    backend.expectOne((r) => r.url === '/api/documents').flush(page);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DocumentListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists documents with their version, status and links', () => {
    setup(['DOCUMENT_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('DOC/26/00004');
    expect(text).toContain('Coiling SOP');
    expect(text).toContain('v3');
    expect(text).toContain('PRODUCT #12');
    expect(text.toLowerCase()).toContain('approved');
  });

  it('hides the upload link from users who cannot upload', () => {
    setup(['DOCUMENT_VIEW']);
    expect(fixture.nativeElement.querySelector('a[href="/documents/new"]')).toBeNull();
  });

  it('offers the upload link to uploaders', () => {
    setup(['DOCUMENT_VIEW', 'DOCUMENT_UPLOAD']);
    expect(fixture.nativeElement.querySelector('a[href="/documents/new"]')).not.toBeNull();
  });

  it('sends the search text when searching', () => {
    setup(['DOCUMENT_VIEW']);
    fixture.componentInstance.searchCtrl.setValue('  sop ');
    fixture.componentInstance.load();
    const req = backend.expectOne((r) => r.url === '/api/documents');
    expect(req.request.params.get('q')).toBe('sop');
    req.flush({ ...page, content: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No documents found');
  });

  it('summarises documents that are linked to nothing with a dash', () => {
    setup(['DOCUMENT_VIEW']);
    expect(fixture.componentInstance.linkSummary({ ...doc, links: [] })).toBe('—');
  });
});
