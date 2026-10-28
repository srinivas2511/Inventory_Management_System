import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { DeliveryChallanListComponent } from './delivery-challan-list.component';

describe('DeliveryChallanListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeliveryChallanListComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(DeliveryChallanListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
