import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { OpenItemListComponent } from './open-item-list.component';

describe('OpenItemListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OpenItemListComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(OpenItemListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
