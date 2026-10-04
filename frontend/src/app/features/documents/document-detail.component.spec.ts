import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { DocumentDetailComponent } from './document-detail.component';
import { DocumentDto } from './documents.api';

function documentDto(overrides: Partial<DocumentDto> = {}): DocumentDto {
  return {
    id: 4,
    documentNumber: 'DOC/26/00004',
    title: 'Spring drawing',
    description: 'Compression spring, 2.0 mm wire',
    categoryCode: 'DRAWING',
    categoryName: 'Drawing',
    status: 'DRAFT',
    statusReason: null,
    currentVersionNo: 2,
    approvedBy: null,
    approvedAt: null,
    createdAt: '2026-10-01T08:00:00Z',
    updatedAt: '2026-10-04T08:00:00Z',
    versions: [
      {
        id: 12,
        versionNo: 2,
        revisionLabel: 'B',
        originalFilename: 'drawing-b.pdf',
        mimeType: 'application/pdf',
        sizeBytes: 2048,
        sha256: 'abcdef0123456789abcdef',
        changeNote: 'Tolerance tightened',
        uploadedBy: 3,
        uploadedAt: '2026-10-04T08:00:00Z',
      },
      {
        id: 11,
        versionNo: 1,
        revisionLabel: 'A',
        originalFilename: 'drawing-a.pdf',
        mimeType: 'application/pdf',
        sizeBytes: 1024,
        sha256: '0123456789abcdef012345',
        changeNote: null,
        uploadedBy: 3,
        uploadedAt: '2026-10-01T08:00:00Z',
      },
    ],
    links: [{ id: 7, entityType: 'PRODUCT', entityId: 12 }],
    ...overrides,
  };
}

describe('DocumentDetailComponent', () => {
  let fixture: ComponentFixture<DocumentDetailComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[], doc: DocumentDto = documentDto()): void {
    TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { paramMap: convertToParamMap({ id: '4' }) } } });
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'u',
      fullName: 'U',
      email: 'u@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(DocumentDetailComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/documents/4').flush(doc);
    fixture.detectChanges();
  }

  const q = (testId: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DocumentDetailComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('shows the document, every version with its revision, and the linked records', () => {
    setup(['DOCUMENT_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('DOC/26/00004');
    expect(text).toContain('Spring drawing');
    expect(text).toContain('v2 (rev B)');
    expect(text).toContain('v1 (rev A)');
    expect(text).toContain('drawing-b.pdf');
    expect(text).toContain('Tolerance tightened');
    expect(text).toContain('PRODUCT #12');
  });

  it('offers no change actions to someone who may only read', () => {
    setup(['DOCUMENT_VIEW']);
    expect(q('submit')).toBeNull();
    expect(q('approve')).toBeNull();
    expect(q('add-version')).toBeNull();
    expect(q('add-link')).toBeNull();
    expect(q('download-current')).not.toBeNull();
  });

  it('lets an uploader submit a draft and shows the new status', () => {
    setup(['DOCUMENT_VIEW', 'DOCUMENT_UPLOAD']);
    q('submit')!.click();
    const req = backend.expectOne('/api/documents/4/submit');
    expect(req.request.method).toBe('POST');
    req.flush(documentDto({ status: 'REVIEW' }));
    fixture.detectChanges();
    expect(q('submit')).toBeNull();
    expect((fixture.nativeElement.textContent as string).toLowerCase()).toContain('review');
  });

  it('lets an approver approve a document in review', () => {
    setup(['DOCUMENT_VIEW', 'DOCUMENT_APPROVE'], documentDto({ status: 'REVIEW' }));
    expect(q('submit')).toBeNull();
    q('approve')!.click();
    backend.expectOne('/api/documents/4/approve').flush(documentDto({ status: 'APPROVED' }));
    fixture.detectChanges();
    expect(q('approve')).toBeNull();
    expect(q('obsolete')).not.toBeNull();
  });

  it('asks for a reason before returning a document to draft', () => {
    setup(['DOCUMENT_VIEW', 'DOCUMENT_APPROVE'], documentDto({ status: 'REVIEW' }));
    q('reject')!.click();
    fixture.detectChanges();
    const confirm = fixture.nativeElement.querySelector('.reason-form button[type="submit"]') as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);

    fixture.componentInstance.reasonCtrl.setValue('Missing torque value');
    fixture.detectChanges();
    expect(confirm.disabled).toBe(false);
    fixture.componentInstance.confirmReason();

    const req = backend.expectOne('/api/documents/4/reject');
    expect(req.request.body).toEqual({ reason: 'Missing torque value' });
    req.flush(documentDto({ status: 'DRAFT', statusReason: 'Missing torque value' }));
    fixture.detectChanges();
    expect(q('status-reason')!.textContent).toContain('Missing torque value');
    expect(fixture.nativeElement.querySelector('.reason-form')).toBeNull();
  });

  it('does not allow a new version of an obsolete document', () => {
    setup(['DOCUMENT_VIEW', 'DOCUMENT_UPLOAD'], documentDto({ status: 'OBSOLETE' }));
    expect(q('add-version')).toBeNull();
  });

  it('keeps the page and shows nothing changed when the server refuses a change', () => {
    setup(['DOCUMENT_VIEW', 'DOCUMENT_UPLOAD']);
    q('submit')!.click();
    backend
      .expectOne('/api/documents/4/submit')
      .flush({ detail: 'A document cannot move from DRAFT to REVIEW' }, { status: 422, statusText: 'Unprocessable' });
    fixture.detectChanges();
    expect(fixture.componentInstance.doc()!.status).toBe('DRAFT');
  });

  it('downloads the current version through the API and hands it to the browser', () => {
    setup(['DOCUMENT_VIEW']);
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    spyOn(URL, 'createObjectURL').and.returnValue('blob:x');
    spyOn(URL, 'revokeObjectURL');

    q('download-current')!.click();
    const req = backend.expectOne((r) => r.url === '/api/documents/4/download');
    expect(req.request.params.has('version')).toBe(false);
    req.flush(new Blob(['%PDF-']), {
      headers: { 'Content-Disposition': `attachment; filename*=UTF-8''drawing-b.pdf` },
    });

    expect(click).toHaveBeenCalled();
  });

  it('downloads an older version by its number', () => {
    setup(['DOCUMENT_VIEW']);
    spyOn(HTMLAnchorElement.prototype, 'click');
    spyOn(URL, 'createObjectURL').and.returnValue('blob:x');
    spyOn(URL, 'revokeObjectURL');

    fixture.componentInstance.download(1);
    const req = backend.expectOne((r) => r.url === '/api/documents/4/download');
    expect(req.request.params.get('version')).toBe('1');
    req.flush(new Blob(['old']));
  });

  it('links the document to another record', () => {
    setup(['DOCUMENT_VIEW', 'DOCUMENT_UPLOAD']);
    fixture.componentInstance.linkForm.patchValue({ entityType: 'BOM', entityId: 33 });
    fixture.componentInstance.addLink();
    const req = backend.expectOne('/api/documents/4/links');
    expect(req.request.body).toEqual({ entityType: 'BOM', entityId: 33 });
    req.flush(
      documentDto({
        links: [
          { id: 7, entityType: 'PRODUCT', entityId: 12 },
          { id: 8, entityType: 'BOM', entityId: 33 },
        ],
      }),
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('BOM #33');
  });

  it('removes a link', () => {
    setup(['DOCUMENT_VIEW', 'DOCUMENT_UPLOAD']);
    (fixture.nativeElement.querySelector('button[aria-label="Remove link"]') as HTMLButtonElement).click();
    const req = backend.expectOne('/api/documents/4/links/7');
    expect(req.request.method).toBe('DELETE');
    req.flush(documentDto({ links: [] }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Not linked to any record');
  });

  it('uploads a new version as multipart', () => {
    setup(['DOCUMENT_VIEW', 'DOCUMENT_UPLOAD']);
    fixture.componentInstance.versionFile.set(new File(['%PDF-'], 'drawing-c.pdf'));
    fixture.componentInstance.versionForm.patchValue({ revisionLabel: 'C', changeNote: 'Added view' });
    fixture.componentInstance.addVersion();
    const req = backend.expectOne('/api/documents/4/versions');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush(documentDto({ currentVersionNo: 3 }));
    expect(fixture.componentInstance.versionFile()).toBeNull();
  });
});
