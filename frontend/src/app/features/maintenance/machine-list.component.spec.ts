import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { MachineListComponent } from './machine-list.component';
import { LookupApi } from '../../shared/lookup.api';

describe('MachineListComponent', () => {
  let fixture: ComponentFixture<MachineListComponent>;
  let http: HttpTestingController;

  const mockLookup: Partial<LookupApi> = {
    workCentres: () => of([{ id: 1, code: 'WC-01', name: 'Assembly' }]),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MachineListComponent, HttpClientTestingModule],
      providers: [
        provideNoopAnimations(),
        { provide: LookupApi, useValue: mockLookup },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(MachineListComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/machines').flush([
      { id: 1, code: 'M-001', name: 'Lathe 1', status: 'AVAILABLE', active: true, workCentreId: 1 },
    ]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show machines in table', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('M-001');
    expect(text).toContain('Lathe 1');
  });
});
