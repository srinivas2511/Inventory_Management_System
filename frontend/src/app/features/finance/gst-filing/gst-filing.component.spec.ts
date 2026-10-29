import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { GstFilingComponent } from './gst-filing.component';

describe('GstFilingComponent', () => {
  let fixture: ComponentFixture<GstFilingComponent>;
  let component: GstFilingComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GstFilingComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
    fixture = TestBed.createComponent(GstFilingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should default fromDate to April 1', () => {
    expect(component.fromDate).toMatch(/-04-01$/);
  });

  it('should have null gstr1 before load', () => {
    expect(component.gstr1()).toBeNull();
  });
});
