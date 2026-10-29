import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { JobWorkOrderListComponent } from './job-work-order-list.component';

describe('JobWorkOrderListComponent', () => {
  let fixture: ComponentFixture<JobWorkOrderListComponent>;
  let component: JobWorkOrderListComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JobWorkOrderListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(JobWorkOrderListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have loading state initially true', () => {
    expect(component.loading()).toBeTrue();
  });

  it('should render table columns', () => {
    expect(component.columns).toContain('docNo');
    expect(component.columns).toContain('status');
    expect(component.columns).toContain('jobCharges');
  });
});
