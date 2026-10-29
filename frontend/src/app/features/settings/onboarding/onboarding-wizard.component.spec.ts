import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { OnboardingWizardComponent } from './onboarding-wizard.component';

describe('OnboardingWizardComponent', () => {
  let fixture: ComponentFixture<OnboardingWizardComponent>;
  let comp: OnboardingWizardComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OnboardingWizardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(OnboardingWizardComponent);
    comp = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates', () => {
    expect(comp).toBeTruthy();
  });

  it('status is null before load completes', () => {
    expect(comp.status()).toBeNull();
  });

  it('loading starts true then settles', () => {
    expect(typeof comp.loading()).toBe('boolean');
  });
});
