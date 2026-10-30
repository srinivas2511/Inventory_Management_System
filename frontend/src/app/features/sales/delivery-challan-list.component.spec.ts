import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogModule } from '@angular/material/dialog';
import { DeliveryChallanListComponent } from './delivery-challan-list.component';

describe('DeliveryChallanListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeliveryChallanListComponent, HttpClientTestingModule, NoopAnimationsModule, MatDialogModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(DeliveryChallanListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should have New Challan button', () => {
    const fixture = TestBed.createComponent(DeliveryChallanListComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const btn = compiled.querySelector('button[aria-label="New Delivery Challan"]');
    expect(btn).toBeTruthy();
  });
});
