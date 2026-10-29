import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ConsolidationComponent } from './consolidation.component';

describe('ConsolidationComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConsolidationComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ConsolidationComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should have correct columns', () => {
    const fixture = TestBed.createComponent(ConsolidationComponent);
    expect(fixture.componentInstance.columns).toContain('code');
    expect(fixture.componentInstance.columns).toContain('amount');
  });
});
