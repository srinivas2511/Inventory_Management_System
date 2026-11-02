import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { OperatorsComponent } from './operators.component';

describe('OperatorsComponent', () => {
  let fixture: ComponentFixture<OperatorsComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OperatorsComponent, HttpClientTestingModule],
      providers: [provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(OperatorsComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/hrm/operators').flush([
      { id: 1, employeeCode: 'EMP-001', fullName: 'Alice Smith', operatorCode: 'OP-001', active: true },
    ]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should display operator rows', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('OP-001');
    expect(text).toContain('Alice Smith');
  });
});
