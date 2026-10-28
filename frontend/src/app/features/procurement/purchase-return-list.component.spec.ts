import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { PurchaseReturnListComponent } from './purchase-return-list.component';

describe('PurchaseReturnListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PurchaseReturnListComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(PurchaseReturnListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
