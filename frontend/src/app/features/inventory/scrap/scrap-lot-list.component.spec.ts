import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ScrapLotListComponent } from './scrap-lot-list.component';

describe('ScrapLotListComponent', () => {
  let fixture: ComponentFixture<ScrapLotListComponent>;
  let comp: ScrapLotListComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScrapLotListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(ScrapLotListComponent);
    comp = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates', () => expect(comp).toBeTruthy());
  it('lots starts empty', () => expect(comp.lots()).toEqual([]));
});
