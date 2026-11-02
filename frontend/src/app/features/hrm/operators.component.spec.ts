import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { OperatorsComponent, OperatorFormDialogComponent } from './operators.component';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

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
      { id: 1, employeeCode: 'EMP-001', fullName: 'Alice Smith', operatorCode: 'OP-001', active: true, version: 0 },
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

  it('should show Add Operator button', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Add Operator');
  });
});

describe('OperatorFormDialogComponent — new', () => {
  let fixture: ComponentFixture<OperatorFormDialogComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OperatorFormDialogComponent, HttpClientTestingModule],
      providers: [
        provideNoopAnimations(),
        { provide: MAT_DIALOG_DATA, useValue: null },
        { provide: MatDialogRef, useValue: { close: jasmine.createSpy('close') } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(OperatorFormDialogComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('should create for new operator', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show New Operator title', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('New Operator');
  });
});

describe('OperatorFormDialogComponent — edit', () => {
  let fixture: ComponentFixture<OperatorFormDialogComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OperatorFormDialogComponent, HttpClientTestingModule],
      providers: [
        provideNoopAnimations(),
        { provide: MAT_DIALOG_DATA, useValue: { id: 1, employeeCode: 'EMP-001', fullName: 'Alice Smith', operatorCode: 'OP-001', active: true, version: 0 } },
        { provide: MatDialogRef, useValue: { close: jasmine.createSpy('close') } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(OperatorFormDialogComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('should create for edit', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show Edit Operator title', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Edit Operator');
  });
});
