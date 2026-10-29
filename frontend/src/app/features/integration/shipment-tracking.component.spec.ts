import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ShipmentTrackingComponent } from './shipment-tracking.component';

describe('ShipmentTrackingComponent', () => {
  let fixture: ComponentFixture<ShipmentTrackingComponent>;
  let comp: ShipmentTrackingComponent;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShipmentTrackingComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(ShipmentTrackingComponent);
    comp = fixture.componentInstance;
    fixture.detectChanges();
  });
  it('creates', () => expect(comp).toBeTruthy());
  it('trackings starts empty', () => expect(comp.trackings()).toEqual([]));
});
