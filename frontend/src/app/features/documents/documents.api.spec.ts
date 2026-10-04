import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DocumentsApi, filenameFromDisposition, formatBytes, multipartBody } from './documents.api';

describe('documents api helpers', () => {
  it('reads the RFC 5987 file name in preference to the quoted one', () => {
    expect(
      filenameFromDisposition(`attachment; filename="=?UTF-8?Q?coiling_sop.pdf?="; filename*=UTF-8''coiling%20sop.pdf`),
    ).toBe('coiling sop.pdf');
  });

  it('falls back to the quoted and the plain forms', () => {
    expect(filenameFromDisposition('attachment; filename="drawing.pdf"')).toBe('drawing.pdf');
    expect(filenameFromDisposition('attachment; filename=drawing.pdf')).toBe('drawing.pdf');
  });

  it('returns null when there is no header or no file name', () => {
    expect(filenameFromDisposition(null)).toBeNull();
    expect(filenameFromDisposition('attachment')).toBeNull();
  });

  it('survives a malformed percent-encoding', () => {
    expect(filenameFromDisposition(`attachment; filename*=UTF-8''100%; filename="ok.pdf"`)).toBe('ok.pdf');
  });

  it('formats sizes', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2.0 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB');
  });

  it('builds a multipart body with the description as a JSON part and the content as the file part', async () => {
    const file = new File(['%PDF-1.7'], 'a.pdf', { type: 'application/pdf' });
    const form = multipartBody({ title: 'T' }, file);
    const meta = form.get('meta') as Blob;
    expect(meta.type).toBe('application/json');
    expect(JSON.parse(await meta.text())).toEqual({ title: 'T' });
    expect((form.get('file') as File).name).toBe('a.pdf');
  });
});

describe('DocumentsApi', () => {
  let api: DocumentsApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(DocumentsApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('sends only the filters that are set', () => {
    api.list({ q: 'sop', category: 'SOP', entityType: 'PRODUCT', entityId: 7 }).subscribe();
    const req = backend.expectOne((r) => r.url === '/api/documents');
    expect(req.request.params.get('q')).toBe('sop');
    expect(req.request.params.get('category')).toBe('SOP');
    expect(req.request.params.get('entityType')).toBe('PRODUCT');
    expect(req.request.params.get('entityId')).toBe('7');
    expect(req.request.params.has('status')).toBe(false);
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 50, number: 0 });
  });

  it('uploads as multipart/form-data', () => {
    api.create({ categoryCode: 'SOP', title: 'T' }, new File(['x'], 'a.txt')).subscribe();
    const req = backend.expectOne('/api/documents');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush({});
  });

  it('downloads a blob and reports the server file name', () => {
    let result = null as { filename: string | null; size: number } | null;
    api.download(3, 2).subscribe((f) => (result = { filename: f.filename, size: f.blob.size }));
    const req = backend.expectOne((r) => r.url === '/api/documents/3/download');
    expect(req.request.params.get('version')).toBe('2');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['abc']), { headers: { 'Content-Disposition': `attachment; filename*=UTF-8''a%20b.pdf` } });
    expect(result).toEqual({ filename: 'a b.pdf', size: 3 });
  });
});
