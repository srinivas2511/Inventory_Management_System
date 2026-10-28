import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { OrderConfirmationListComponent } from './order-confirmation-list.component';

describe('OrderConfirmationListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderConfirmationListComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(OrderConfirmationListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
