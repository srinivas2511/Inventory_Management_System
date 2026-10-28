import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { PaymentListComponent } from './payment-list.component';

describe('PaymentListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentListComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(PaymentListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
