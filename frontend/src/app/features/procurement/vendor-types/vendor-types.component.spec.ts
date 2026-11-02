import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { VendorTypesComponent } from './vendor-types.component';
import { VendorTypeApi } from './vendor-type.api';

describe('VendorTypesComponent', () => {
  let fixture: ComponentFixture<VendorTypesComponent>;

  const mockApi: Partial<VendorTypeApi> = {
    list: () => of([{ id: 1, code: 'DOMESTIC', description: 'Domestic Vendor', active: true, version: 0 }]),
    create: (r) => of({ id: 2, code: r.code, description: r.description, active: true, version: 0 }),
    update: (_id, r) => of({ id: 1, code: r.code, description: r.description, active: true, version: 1 }),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VendorTypesComponent],
      providers: [
        provideNoopAnimations(),
        { provide: VendorTypeApi, useValue: mockApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(VendorTypesComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should display loaded vendor types', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('DOMESTIC');
  });
});
