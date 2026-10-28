import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ApprovalRulesComponent } from './approval-rules.component';

describe('ApprovalRulesComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ApprovalRulesComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ApprovalRulesComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
