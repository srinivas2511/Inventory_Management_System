import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { WorkCentresComponent } from './work-centres.component';
import { WorkCentreApi } from './work-centre.api';

describe('WorkCentresComponent', () => {
  let fixture: ComponentFixture<WorkCentresComponent>;

  const mockApi: Partial<WorkCentreApi> = {
    list: () => of([{ id: 1, code: 'WC-01', name: 'Assembly', description: 'Assembly line', active: true, version: 0 }]),
    create: (r) => of({ id: 2, code: r.code, name: r.name, description: r.description ?? undefined, active: true, version: 0 }),
    update: (_id, r) => of({ id: 1, code: r.code, name: r.name, description: r.description ?? undefined, active: true, version: 1 }),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkCentresComponent],
      providers: [
        provideNoopAnimations(),
        { provide: WorkCentreApi, useValue: mockApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(WorkCentresComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show loaded work centres', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('WC-01');
  });
});
