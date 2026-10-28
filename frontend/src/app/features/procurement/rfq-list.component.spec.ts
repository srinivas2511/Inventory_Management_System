import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RfqListComponent } from './rfq-list.component';

describe('RfqListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RfqListComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(RfqListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
