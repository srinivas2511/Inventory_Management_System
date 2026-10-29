import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { CapacityBoardComponent } from './capacity-board.component';

describe('CapacityBoardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CapacityBoardComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(CapacityBoardComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should have correct columns', () => {
    const fixture = TestBed.createComponent(CapacityBoardComponent);
    expect(fixture.componentInstance.columns).toContain('machine');
    expect(fixture.componentInstance.columns).toContain('load');
  });
});
