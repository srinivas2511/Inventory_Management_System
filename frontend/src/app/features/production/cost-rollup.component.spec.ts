import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { CostRollupComponent, CostRollupLogDto } from './cost-rollup.component';

const log: CostRollupLogDto = {
  id: 1,
  materialId: 10,
  rolledCost: 50.0,
  bomId: null,
  depth: 0,
  rolledAt: '2026-10-28T08:00:00Z',
};

describe('CostRollupComponent', () => {
  let fixture: ComponentFixture<CostRollupComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CostRollupComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(CostRollupComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/production/cost-rollup').flush([log]);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('renders the table with one row', () => {
    const rows = fixture.nativeElement.querySelectorAll('tr[mat-row]');
    expect(rows.length).toBe(1);
  });

  it('shows material id', () => {
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('10');
  });

  it('shows rolled cost', () => {
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('50.0000');
  });

  it('has a Run Roll-up button', () => {
    const buttons = fixture.nativeElement.querySelectorAll('button');
    const labels: string[] = Array.from<HTMLElement>(buttons as NodeListOf<HTMLElement>).map(
      (b) => b.textContent ?? '',
    );
    expect(labels.some((l) => l.includes('Run Roll-up'))).toBeTrue();
  });
});
