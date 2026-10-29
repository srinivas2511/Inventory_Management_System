import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { AllDocumentsComponent } from './all-documents.component';

describe('AllDocumentsComponent', () => {
  let fixture: ComponentFixture<AllDocumentsComponent>;
  let comp: AllDocumentsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AllDocumentsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(AllDocumentsComponent);
    comp = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates', () => {
    expect(comp).toBeTruthy();
  });

  it('rows starts empty', () => {
    expect(comp.rows()).toEqual([]);
  });

  it('default pageSize is 50', () => {
    expect(comp.pageSize).toBe(50);
  });
});
