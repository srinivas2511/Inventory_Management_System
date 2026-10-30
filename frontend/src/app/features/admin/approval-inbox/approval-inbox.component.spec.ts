import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ApprovalInboxComponent, ApprovalRequestDto } from './approval-inbox.component';

describe('ApprovalInboxComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ApprovalInboxComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('creates', () => {
    const fix = TestBed.createComponent(ApprovalInboxComponent);
    fix.detectChanges();
    httpMock.expectOne('/api/approvals/pending').flush([]);
    httpMock.expectOne('/api/approvals/history').flush([]);
    expect(fix.componentInstance).toBeTruthy();
  });

  it('loads pending and history on init', () => {
    const fix = TestBed.createComponent(ApprovalInboxComponent);
    const comp = fix.componentInstance;
    fix.detectChanges();

    const pendingReq = httpMock.expectOne('/api/approvals/pending');
    const histReq = httpMock.expectOne('/api/approvals/history');

    const pendingItem: ApprovalRequestDto = {
      id: 1, documentType: 'STOCK_ADJUSTMENT', documentId: 10, stepNo: 1,
      requiredPermission: 'ADJUST_APPROVE', status: 'PENDING',
      requestedBy: 5, decidedBy: null, decidedAt: null, comment: null,
      createdAt: '2026-10-30T10:00:00Z',
    };
    pendingReq.flush([pendingItem]);
    histReq.flush([]);

    expect(comp.pending().length).toBe(1);
    expect(comp.history().length).toBe(0);
  });

  it('approve removes item from pending and reloads history', () => {
    const fix = TestBed.createComponent(ApprovalInboxComponent);
    const comp = fix.componentInstance;
    fix.detectChanges();
    httpMock.expectOne('/api/approvals/pending').flush([
      { id: 2, documentType: 'TEST', documentId: 1, stepNo: 1, requiredPermission: 'P',
        status: 'PENDING', requestedBy: 1, decidedBy: null, decidedAt: null, comment: null,
        createdAt: '2026-10-30T10:00:00Z' } as ApprovalRequestDto,
    ]);
    httpMock.expectOne('/api/approvals/history').flush([]);

    comp.approve(comp.pending()[0]);
    httpMock.expectOne('/api/approvals/2/approve').flush({ id: 2, status: 'APPROVED' });
    httpMock.expectOne('/api/approvals/history').flush([]);

    expect(comp.pending().length).toBe(0);
  });
});
