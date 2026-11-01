import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { TeamsComponent } from './teams.component';

describe('TeamsComponent', () => {
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TeamsComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('should create', () => {
    const fixture = TestBed.createComponent(TeamsComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
    backend.expectOne('/api/admin/teams').flush([]);
  });

  it('sends teamCode (not code) on save — DEF-009 regression', () => {
    const fixture = TestBed.createComponent(TeamsComponent);
    fixture.detectChanges();
    backend.expectOne('/api/admin/teams').flush([]);

    const comp = fixture.componentInstance;
    comp.startCreate();
    comp.form.setValue({ code: 'QA-TEAM-01', name: 'QA Testing Team', teamType: 'FUNCTIONAL', description: '' });
    comp.save();

    const req = backend.expectOne((r) => r.method === 'POST' && r.url === '/api/admin/teams');
    expect(req.request.body['teamCode']).toBe('QA-TEAM-01');
    expect(req.request.body['name']).toBe('QA Testing Team');
    expect(req.request.body['code']).toBeUndefined();
    req.flush({ id: 1, code: 'QA-TEAM-01', name: 'QA Testing Team', description: null, teamType: 'FUNCTIONAL', active: true, members: [], notificationSettings: null, moduleAccess: null });
  });
});
