import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { DocumentAttachmentsComponent } from './document-attachments.component';
import { DocumentSummaryDto } from './documents.api';

@Component({
  standalone: true,
  imports: [DocumentAttachmentsComponent],
  template: `<app-document-attachments [entityType]="type" [entityId]="id" defaultCategory="CAPA_EVIDENCE" />`,
})
class HostComponent {
  type = 'CAPA';
  id = 9;
}

const attached: DocumentSummaryDto = {
  id: 4,
  documentNumber: 'DOC/26/00004',
  title: 'Measurement record',
  categoryCode: 'CAPA_EVIDENCE',
  categoryName: 'CAPA evidence',
  status: 'DRAFT',
  currentVersionNo: 1,
  updatedAt: '2026-10-04T08:00:00Z',
  links: [{ id: 1, entityType: 'CAPA', entityId: 9 }],
};

describe('DocumentAttachmentsComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
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
    fixture = TestBed.createComponent(HostComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  const q = (testId: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('asks for the documents of exactly that record and lists them', () => {
    setup(['DOCUMENT_VIEW']);
    const req = backend.expectOne((r) => r.url === '/api/documents');
    expect(req.request.params.get('entityType')).toBe('CAPA');
    expect(req.request.params.get('entityId')).toBe('9');
    req.flush({ content: [attached], totalElements: 1, totalPages: 1, size: 100, number: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('DOC/26/00004');
    expect(fixture.nativeElement.textContent).toContain('Measurement record');
    expect(q('attach')).toBeNull();
  });

  it('says so when nothing is attached', () => {
    setup(['DOCUMENT_VIEW']);
    backend
      .expectOne((r) => r.url === '/api/documents')
      .flush({ content: [], totalElements: 0, totalPages: 0, size: 100, number: 0 });
    fixture.detectChanges();
    expect(q('no-documents')).not.toBeNull();
  });

  it('does not even ask the server for someone who cannot read documents', () => {
    setup(['QUALITY_VIEW']);
    backend.expectNone((r) => r.url === '/api/documents');
    expect(q('attachments')).toBeNull();
  });

  it('attaches a new document linked to the record, preselecting the default category', () => {
    setup(['DOCUMENT_VIEW', 'DOCUMENT_UPLOAD']);
    backend
      .expectOne((r) => r.url === '/api/documents')
      .flush({ content: [], totalElements: 0, totalPages: 0, size: 100, number: 0 });
    fixture.detectChanges();

    q('attach')!.click();
    backend.expectOne('/api/document-categories').flush([
      {
        id: 1,
        categoryCode: 'CAPA_EVIDENCE',
        name: 'CAPA evidence',
        retentionYears: 5,
        requiredAuthority: null,
        active: true,
      },
      { id: 2, categoryCode: 'SOP', name: 'SOP', retentionYears: 5, requiredAuthority: null, active: true },
    ]);
    fixture.detectChanges();

    const attachments = fixture.debugElement.children[0].componentInstance as DocumentAttachmentsComponent;
    expect(attachments.form.getRawValue().categoryCode).toBe('CAPA_EVIDENCE');
    attachments.form.patchValue({ title: 'Gauge readings' });
    attachments.file.set(new File(['%PDF-'], 'readings.pdf'));
    attachments.attach();

    const create = backend.expectOne('/api/documents');
    expect(create.request.method).toBe('POST');
    const form = create.request.body as FormData;
    return (form.get('meta') as Blob).text().then((text) => {
      expect(JSON.parse(text)).toEqual({
        categoryCode: 'CAPA_EVIDENCE',
        title: 'Gauge readings',
        revisionLabel: null,
        links: [{ entityType: 'CAPA', entityId: 9 }],
      });
      create.flush({ id: 5 });
      backend
        .expectOne((r) => r.url === '/api/documents' && r.method === 'GET')
        .flush({ content: [attached], totalElements: 1, totalPages: 1, size: 100, number: 0 });
      expect(attachments.attaching()).toBe(false);
    });
  });
});
