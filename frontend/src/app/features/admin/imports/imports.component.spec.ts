import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ImportsComponent } from './imports.component';

describe('ImportsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ImportsComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ImportsComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
