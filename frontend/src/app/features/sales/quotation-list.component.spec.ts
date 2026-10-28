import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { QuotationListComponent } from './quotation-list.component';

describe('QuotationListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QuotationListComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(QuotationListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
