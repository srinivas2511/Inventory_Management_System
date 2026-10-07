import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RoleDto } from '../admin.api';
import { RoleDetailDialogComponent } from './role-detail-dialog.component';
import { RoleListComponent } from './role-list.component';

const ROLE: RoleDto = {
  id: 3,
  code: 'STORE_MANAGER',
  name: 'Store manager',
  description: 'Runs the stores',
  systemRole: true,
  permissions: [
    { id: 1, code: 'INVENTORY_VIEW', module: 'INVENTORY', description: 'View stock' },
    { id: 2, code: 'INVENTORY_RECEIVE', module: 'INVENTORY', description: null },
    { id: 3, code: 'PURCHASE_VIEW', module: 'PURCHASE', description: null },
  ],
};

describe('RoleListComponent', () => {
  let fixture: ComponentFixture<RoleListComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RoleListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RoleListComponent);
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url: '/api/admin/roles' }).flush([ROLE]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('lists each role with its permission count', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('STORE_MANAGER');
    expect(text).toContain('3 permissions');
  });

  it('opens the permissions of a role from the list', () => {
    const open = spyOn(TestBed.inject(MatDialog), 'open').and.returnValue({} as MatDialogRef<unknown>);
    (
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="role-STORE_MANAGER"]') as HTMLButtonElement
    ).click();

    expect(open).toHaveBeenCalledWith(RoleDetailDialogComponent, jasmine.objectContaining({ data: { role: ROLE } }));
  });
});

describe('RoleDetailDialogComponent', () => {
  it('groups the permissions by module, in order', () => {
    TestBed.configureTestingModule({
      imports: [RoleDetailDialogComponent, NoopAnimationsModule],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: { role: ROLE } }],
    });
    const fixture = TestBed.createComponent(RoleDetailDialogComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const modules = Array.from(el.querySelectorAll('h3')).map((h) => h.textContent?.trim());
    expect(modules).toEqual(['INVENTORY', 'PURCHASE']);
    expect(el.querySelector('[data-testid="module-INVENTORY"]')?.textContent).toContain('INVENTORY_RECEIVE');
    expect(el.querySelector('[data-testid="module-PURCHASE"]')?.textContent).toContain('PURCHASE_VIEW');
    expect(el.textContent).toContain('3 permissions');
    expect(el.textContent).toContain('Runs the stores');
  });

  it('says so when a role holds nothing', () => {
    TestBed.configureTestingModule({
      imports: [RoleDetailDialogComponent, NoopAnimationsModule],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: { role: { ...ROLE, permissions: [] } } }],
    });
    const fixture = TestBed.createComponent(RoleDetailDialogComponent);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('This role holds no permissions.');
  });
});
