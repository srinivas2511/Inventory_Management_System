import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { UserDto } from '../admin.api';
import { PlantDto, UserPlantsDto } from '../plants/plant.api';
import { UserPlantsDialogComponent } from './user-plants-dialog.component';

const USER: UserDto = {
  id: 7,
  username: 'ravi',
  employeeCode: null,
  fullName: 'Ravi Kumar',
  email: 'ravi@example.com',
  phone: null,
  active: true,
  mustChangePassword: false,
  locked: false,
  lockedUntil: null,
  lastLoginAt: null,
  passwordChangedAt: null,
  roles: ['OPERATOR'],
  version: 1,
};

const plant = (id: number, code: string, active = true): PlantDto => ({
  id,
  plantCode: code,
  name: `Plant ${code}`,
  companyId: 1,
  address: null,
  active,
});

describe('UserPlantsDialogComponent', () => {
  let fixture: ComponentFixture<UserPlantsDialogComponent>;
  let http: HttpTestingController;
  let close: jasmine.Spy;

  function open(assigned: UserPlantsDto, plants: PlantDto[]): void {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [UserPlantsDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { user: USER } },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(UserPlantsDialogComponent);
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url: '/api/plants' }).flush(plants);
    http.expectOne({ method: 'GET', url: '/api/plants/users/7' }).flush(assigned);
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  it('lists the plants and ticks the ones the user is limited to', () => {
    open({ userId: 7, plantIds: [2], allAccess: false }, [plant(1, 'P001'), plant(2, 'P002'), plant(3, 'P003', false)]);

    const el: HTMLElement = fixture.nativeElement;
    const options = Array.from(el.querySelectorAll('mat-list-option'));
    expect(options.length).withContext('inactive plants the user is not on are not offered').toBe(2);
    expect(options[0].getAttribute('aria-selected')).toBe('false');
    expect(options[1].getAttribute('aria-selected')).toBe('true');
    expect(el.querySelector('[data-testid="plants-all-access"]')).toBeNull();
  });

  it('saves the chosen plants with a PUT and closes with the result', () => {
    open({ userId: 7, plantIds: [1], allAccess: false }, [plant(1, 'P001'), plant(2, 'P002')]);

    (fixture.nativeElement.querySelector('[data-testid="plants-save"]') as HTMLButtonElement).click();
    const req = http.expectOne({ method: 'PUT', url: '/api/plants/users/7' });
    expect(req.request.body).toEqual({ plantIds: [1] });
    req.flush({ userId: 7, plantIds: [1], allAccess: false });

    expect(close).toHaveBeenCalledWith({ userId: 7, plantIds: [1], allAccess: false });
  });

  it('warns when a role grants access to all plants', () => {
    open({ userId: 7, plantIds: [], allAccess: true }, [plant(1, 'P001')]);

    expect(fixture.nativeElement.querySelector('[data-testid="plants-all-access"]')).not.toBeNull();
  });

  it('shows the server message when saving fails and stays open', () => {
    open({ userId: 7, plantIds: [], allAccess: false }, [plant(1, 'P001')]);

    (fixture.nativeElement.querySelector('[data-testid="plants-save"]') as HTMLButtonElement).click();
    http
      .expectOne({ method: 'PUT', url: '/api/plants/users/7' })
      .flush(
        { status: 404, title: 'Not found', detail: 'Plant not found: 9', code: 'NOT_FOUND' },
        { status: 404, statusText: 'Not Found' },
      );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="plants-error"]').textContent).toContain(
      'Plant not found',
    );
    expect(close).not.toHaveBeenCalled();
  });
});
