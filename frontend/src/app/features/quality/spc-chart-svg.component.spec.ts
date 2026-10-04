import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SpcChartSvgComponent } from './spc-chart-svg.component';

describe('SpcChartSvgComponent', () => {
  let fixture: ComponentFixture<SpcChartSvgComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SpcChartSvgComponent] }).compileComponents();
    fixture = TestBed.createComponent(SpcChartSvgComponent);
  });

  function render(inputs: Partial<SpcChartSvgComponent>): void {
    Object.assign(fixture.componentInstance, inputs);
    fixture.componentInstance.ngOnChanges();
    fixture.detectChanges();
  }

  it('says so when there is no data', () => {
    render({ title: 'X-bar chart', points: [] });
    expect(fixture.nativeElement.textContent).toContain('No data points yet');
    expect(fixture.nativeElement.querySelector('svg')).toBeNull();
  });

  it('draws the three control lines and marks out-of-control points', () => {
    render({
      title: 'X-bar chart',
      points: [
        { value: 10, outOfControl: false, label: 'one' },
        { value: 10.1, outOfControl: false, label: 'two' },
        { value: 10.6, outOfControl: true, label: 'three' },
      ],
      cl: 10,
      ucl: 10.3,
      lcl: 9.7,
    });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('line.ref').length).toBe(3);
    expect(el.querySelector('line.ref.UCL')).not.toBeNull();
    expect(el.querySelectorAll('circle.pt').length).toBe(3);
    expect(el.querySelectorAll('circle.pt.ooc').length).toBe(1);
    expect(el.textContent).toContain('UCL 10.3');
    expect(el.querySelector('polyline')).not.toBeNull();
  });
});
