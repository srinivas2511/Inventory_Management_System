import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { AdvanceReceiptsComponent } from './advance-receipts.component';

describe('AdvanceReceiptsComponent', () => {
  let fixture: ComponentFixture<AdvanceReceiptsComponent>;
  let component: AdvanceReceiptsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdvanceReceiptsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(AdvanceReceiptsComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have form controls', () => {
    expect(component.form.contains('customerId')).toBeTrue();
    expect(component.form.contains('receiptDate')).toBeTrue();
    expect(component.form.contains('amount')).toBeTrue();
  });

  it('should be invalid when empty', () => {
    expect(component.form.invalid).toBeTrue();
  });
});
