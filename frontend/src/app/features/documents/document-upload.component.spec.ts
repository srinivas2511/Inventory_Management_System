import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { DocumentUploadComponent } from './document-upload.component';
import { DocumentCategoryDto } from './documents.api';

const categories: DocumentCategoryDto[] = [
  { id: 1, categoryCode: 'SOP', name: 'SOP', retentionYears: 5, requiredAuthority: null, active: true },
  {
    id: 2,
    categoryCode: 'DRAWING',
    name: 'Drawing',
    retentionYears: 10,
    requiredAuthority: 'DRAWING_MANAGE',
    active: true,
  },
  { id: 3, categoryCode: 'OLD', name: 'Old', retentionYears: null, requiredAuthority: null, active: false },
];

describe('DocumentUploadComponent', () => {
  let fixture: ComponentFixture<DocumentUploadComponent>;
  let backend: HttpTestingController;
  let router: Router;

  function setup(query: Record<string, string> = {}): void {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
    });
    fixture = TestBed.createComponent(DocumentUploadComponent);
    backend = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
    backend.expectOne('/api/document-categories').flush(categories);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DocumentUploadComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('offers only active categories', () => {
    setup();
    expect(fixture.componentInstance.categories().map((c) => c.categoryCode)).toEqual(['SOP', 'DRAWING']);
  });

  it('cannot be submitted without a title and a file', () => {
    setup();
    const button = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    fixture.componentInstance.form.patchValue({ categoryCode: 'SOP', title: 'Coiling SOP' });
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    fixture.componentInstance.file.set(new File(['%PDF-'], 'a.pdf'));
    fixture.detectChanges();
    expect(button.disabled).toBe(false);
  });

  it('pre-fills the category and the link from the query string', () => {
    setup({ entityType: 'CAPA', entityId: '9', category: 'DRAWING' });
    const v = fixture.componentInstance.form.getRawValue();
    expect(v.linkType).toBe('CAPA');
    expect(v.linkId).toBe(9);
    expect(v.categoryCode).toBe('DRAWING');
  });

  it('ignores a pre-selected category that does not exist', () => {
    setup({ category: 'NOPE' });
    expect(fixture.componentInstance.form.getRawValue().categoryCode).toBe('');
  });

  it('uploads multipart, trimming the text, and opens the new document', () => {
    setup();
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);
    fixture.componentInstance.form.patchValue({
      categoryCode: 'SOP',
      title: '  Coiling SOP ',
      revisionLabel: ' A ',
      linkType: 'PRODUCT',
      linkId: 5,
    });
    fixture.componentInstance.file.set(new File(['%PDF-'], 'a.pdf'));

    fixture.componentInstance.submit();

    const req = backend.expectOne('/api/documents');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    const form = req.request.body as FormData;
    return (form.get('meta') as Blob).text().then((text) => {
      expect(JSON.parse(text)).toEqual({
        categoryCode: 'SOP',
        title: 'Coiling SOP',
        description: null,
        revisionLabel: 'A',
        changeNote: null,
        links: [{ entityType: 'PRODUCT', entityId: 5 }],
      });
      req.flush({ id: 21, documentNumber: 'DOC/26/00021' });
      expect(navigate).toHaveBeenCalledWith(['/documents', 21]);
    });
  });

  it('stays on the page and re-enables the form when the server refuses the upload', () => {
    setup();
    fixture.componentInstance.form.patchValue({ categoryCode: 'SOP', title: 'T' });
    fixture.componentInstance.file.set(new File(['x'], 'a.exe'));
    fixture.componentInstance.submit();
    backend
      .expectOne('/api/documents')
      .flush({ detail: 'Files of type .exe are not accepted' }, { status: 415, statusText: 'Unsupported' });
    expect(fixture.componentInstance.saving()).toBe(false);
  });
});
