import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogModule } from '@angular/material/dialog';
import { OrderConfirmationListComponent } from './order-confirmation-list.component';

describe('OrderConfirmationListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderConfirmationListComponent, HttpClientTestingModule, NoopAnimationsModule, MatDialogModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(OrderConfirmationListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should have New OC button', () => {
    const fixture = TestBed.createComponent(OrderConfirmationListComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const btn = compiled.querySelector('button[aria-label="New Order Confirmation"]');
    expect(btn).toBeTruthy();
  });
});
