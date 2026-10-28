import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TransportersComponent } from './transporters.component';

describe('TransportersComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TransportersComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(TransportersComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
