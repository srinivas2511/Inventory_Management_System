import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { PlantFieldComponent } from './plant-field.component';

const signedIn = (): void => {
  TestBed.inject(SessionStore).setUser({
    id: 1,
    username: 'asha',
    fullName: 'Asha Rao',
    roles: ['ADMIN'],
    permissions: [],
    mustChangePassword: false,
  });
};

describe('PlantFieldComponent', () => {
  let fixture: ComponentFixture<PlantFieldComponent>;
  let http: HttpTestingController;

  function render(options: { id: number; code: string; name: string }[]): void {
    TestBed.configureTestingModule({
      imports: [PlantFieldComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    signedIn();
    fixture = TestBed.createComponent(PlantFieldComponent);
    fixture.componentRef.setInput('control', new FormControl<number | null>(null));
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url: '/api/lookups/plants' }).flush(options);
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  it('renders nothing when the user has only one plant, so a single-plant install is unchanged', () => {
    render([{ id: 1, code: 'P001', name: 'Pune Main Plant' }]);

    expect(fixture.nativeElement.querySelector('[data-testid="plant-select"]')).toBeNull();
  });

  it('offers the plants when the user may choose between several', () => {
    render([
      { id: 1, code: 'P001', name: 'Pune Main Plant' },
      { id: 2, code: 'P002', name: 'Nashik' },
    ]);

    expect(fixture.nativeElement.querySelector('[data-testid="plant-select"]')).not.toBeNull();
  });

  it('renders nothing when the lookup is refused', () => {
    TestBed.configureTestingModule({
      imports: [PlantFieldComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    signedIn();
    fixture = TestBed.createComponent(PlantFieldComponent);
    fixture.componentRef.setInput('control', new FormControl<number | null>(null));
    fixture.detectChanges();
    http
      .expectOne({ method: 'GET', url: '/api/lookups/plants' })
      .flush({ status: 403, title: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="plant-select"]')).toBeNull();
  });

  it('asks for nothing when nobody is signed in', () => {
    TestBed.configureTestingModule({
      imports: [PlantFieldComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PlantFieldComponent);
    fixture.componentRef.setInput('control', new FormControl<number | null>(null));
    fixture.detectChanges();

    http.expectNone('/api/lookups/plants');
  });
});
