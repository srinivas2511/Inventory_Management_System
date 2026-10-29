import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { BusyExportComponent } from './busy-export.component';

describe('BusyExportComponent', () => {
  let fixture: ComponentFixture<BusyExportComponent>;
  let comp: BusyExportComponent;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BusyExportComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(BusyExportComponent);
    comp = fixture.componentInstance;
    fixture.detectChanges();
  });
  it('creates', () => expect(comp).toBeTruthy());
  it('queue starts empty', () => expect(comp.queue()).toEqual([]));
});
