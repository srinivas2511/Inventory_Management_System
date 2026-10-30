import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TdsEntriesComponent } from './tds-entries.component';

describe('TdsEntriesComponent', () => {
  let fixture: ComponentFixture<TdsEntriesComponent>;
  let component: TdsEntriesComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TdsEntriesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(TdsEntriesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialise with empty entries', () => {
    expect(component.entries()).toEqual([]);
  });

  it('should have form fields', () => {
    expect(component.form.get('direction')).toBeTruthy();
    expect(component.form.get('baseAmount')).toBeTruthy();
    expect(component.form.get('tdsAmount')).toBeTruthy();
  });
});
