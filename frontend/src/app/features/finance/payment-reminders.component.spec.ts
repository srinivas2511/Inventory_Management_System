import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { PaymentRemindersComponent } from './payment-reminders.component';

describe('PaymentRemindersComponent', () => {
  let fixture: ComponentFixture<PaymentRemindersComponent>;
  let component: PaymentRemindersComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentRemindersComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(PaymentRemindersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialise with empty reminders list', () => {
    expect(component.reminders()).toEqual([]);
  });

  it('should toggle form visibility', () => {
    expect(component.showForm()).toBeFalse();
    component.openScheduleDialog();
    expect(component.showForm()).toBeTrue();
  });
});
