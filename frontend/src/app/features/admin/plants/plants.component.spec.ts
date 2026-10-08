import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { PlantDto, PlantSettingDto } from './plant.api';
import { PlantsComponent } from './plants.component';

const PLANTS: PlantDto[] = [
  { id: 1, plantCode: 'P001', name: 'Pune Main Plant', companyId: 1, address: null, active: true },
  { id: 2, plantCode: 'P002', name: 'Nashik', companyId: 1, address: 'MIDC', active: true },
];

const COMPANY: PlantSettingDto[] = [
  { key: 'default_warehouse_id', value: '', source: 'SYSTEM' },
  { key: 'timezone', value: 'UTC', source: 'COMPANY' },
  { key: 'production_calendar', value: 'DEFAULT', source: 'SYSTEM' },
  { key: 'costing_method', value: 'MOVING_AVERAGE', source: 'SYSTEM' },
];

describe('PlantsComponent', () => {
  let fixture: ComponentFixture<PlantsComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PlantsComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PlantsComponent);
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url: '/api/plants' }).flush(PLANTS);
    http.expectOne({ method: 'GET', url: '/api/plants/company-settings' }).flush(COMPANY);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const el = () => fixture.nativeElement as HTMLElement;
  const click = (testId: string) => (el().querySelector(`[data-testid="${testId}"]`) as HTMLElement).click();

  it('lists the plants and the company defaults with the level each value comes from', () => {
    expect(el().querySelectorAll('[data-testid="plant-table"] tr[mat-row]').length).toBe(2);
    expect(el().textContent).toContain('Pune Main Plant');
    expect(el().querySelector('[data-testid="setting-timezone"]')?.textContent).toContain('company default');
    expect(el().querySelector('[data-testid="setting-costing_method"]')?.textContent).toContain('system default');
    expect(el().querySelector('[data-testid="clear-timezone"]'))
      .withContext('company override can be cleared')
      .not.toBeNull();
    expect(el().querySelector('[data-testid="clear-costing_method"]')).toBeNull();
  });

  it('creates a plant with a POST and reloads the list', () => {
    click('add-plant');
    fixture.detectChanges();
    const code = el().querySelector('[data-testid="plant-code"]') as HTMLInputElement;
    const name = el().querySelector('[data-testid="plant-name"]') as HTMLInputElement;
    code.value = 'P003';
    code.dispatchEvent(new Event('input'));
    name.value = 'Satara';
    name.dispatchEvent(new Event('input'));
    click('save-plant');

    const req = http.expectOne({ method: 'POST', url: '/api/plants' });
    expect(req.request.body).toEqual({ plantCode: 'P003', name: 'Satara', address: null, active: true });
    req.flush({ id: 3, plantCode: 'P003', name: 'Satara', companyId: 1, address: null, active: true });
    http.expectOne({ method: 'GET', url: '/api/plants' }).flush(PLANTS);
  });

  it('shows the server message when a plant cannot be saved', () => {
    click('edit-plant');
    fixture.detectChanges();
    click('save-plant');
    http
      .expectOne({ method: 'PUT', url: '/api/plants/1' })
      .flush(
        { status: 409, title: 'Duplicate', detail: 'Plant code already exists: P001', code: 'DUPLICATE_KEY' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(el().querySelector('[data-testid="plant-error"]')?.textContent).toContain('already exists');
  });

  it('saves a company default and clears it again', () => {
    const input = el().querySelector('[data-testid="setting-production_calendar"] input') as HTMLInputElement;
    input.value = 'TWO_SHIFT';
    input.dispatchEvent(new Event('input'));
    click('save-production_calendar');

    const put = http.expectOne({ method: 'PUT', url: '/api/plants/settings' });
    expect(put.request.body).toEqual({ plantId: null, key: 'production_calendar', value: 'TWO_SHIFT' });
    put.flush(null);
    http.expectOne({ method: 'GET', url: '/api/plants/company-settings' }).flush(COMPANY);
    fixture.detectChanges();

    click('clear-timezone');
    const del = http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/plants/settings');
    expect(del.request.params.get('key')).toBe('timezone');
    expect(del.request.params.has('plantId')).toBeFalse();
    del.flush(null);
    http.expectOne({ method: 'GET', url: '/api/plants/company-settings' }).flush(COMPANY);
  });
});
