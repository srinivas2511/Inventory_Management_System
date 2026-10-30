import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { EwayBillComponent } from './eway-bill.component';

describe('EwayBillComponent', () => {
  let fixture: ComponentFixture<EwayBillComponent>;
  let component: EwayBillComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EwayBillComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(EwayBillComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have invoiceId control required', () => {
    expect(component.generateForm.contains('invoiceId')).toBeTrue();
    expect(component.generateForm.get('invoiceId')?.hasError('required')).toBeTrue();
  });

  it('should start with empty bills list', () => {
    expect(component.bills()).toEqual([]);
  });
});
