import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { ToastService } from '../../../core/notification/toast.service';
import { DialogService } from '../../../shared/dialogs/dialog.service';
import { RolesApi } from '../api/roles.api';
import { RoleResponse } from '../models';
import { RolesPageComponent } from './roles-page.component';

const summary = { id: 5, code: 'ENGINEER', name: 'Engineer', systemRole: true, permissionCount: 1, userCount: 2 };
const full: RoleResponse = {
  id: 5,
  code: 'ENGINEER',
  name: 'Engineer',
  systemRole: true,
  permissions: ['PRODUCT_VIEW'],
  userCount: 2,
  version: 3,
};

describe('RolesPageComponent', () => {
  let fixture: ComponentFixture<RolesPageComponent>;
  let api: jasmine.SpyObj<RolesApi>;
  let dialogs: jasmine.SpyObj<DialogService>;

  const q = (id: string) => fixture.nativeElement.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;

  beforeEach(async () => {
    api = jasmine.createSpyObj<RolesApi>('RolesApi', ['list', 'get', 'permissions', 'setPermissions', 'delete']);
    api.list.and.returnValue(of([summary]));
    api.get.and.returnValue(of(full));
    api.permissions.and.returnValue(
      of([
        { id: 1, code: 'PRODUCT_VIEW', module: 'Product' },
        { id: 2, code: 'PRODUCT_MANAGE', module: 'Product' },
      ]),
    );
    dialogs = jasmine.createSpyObj<DialogService>('DialogService', ['askReason', 'confirm']);
    await TestBed.configureTestingModule({
      imports: [RolesPageComponent],
      providers: [
        provideNoopAnimations(),
        { provide: RolesApi, useValue: api },
        { provide: DialogService, useValue: dialogs },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error', 'info']) },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(RolesPageComponent);
    fixture.detectChanges();
  });

  it('prompts to pick a role, then shows its permissions', () => {
    expect(q('pick-role')).not.toBeNull();
    q('role-ENGINEER')!.click();
    fixture.detectChanges();
    expect(q('perm-PRODUCT_VIEW')).not.toBeNull();
    expect(q('delete-role')).toBeNull(); // system roles cannot be deleted
  });

  it('enables save only when the permission set changed, and saves with the reason', async () => {
    q('role-ENGINEER')!.click();
    fixture.detectChanges();
    expect((q('save-permissions') as HTMLButtonElement).disabled).toBeTrue();
    const box = q('perm-PRODUCT_MANAGE')!.querySelector('input') as HTMLInputElement;
    box.click();
    fixture.detectChanges();
    expect((q('save-permissions') as HTMLButtonElement).disabled).toBeFalse();

    dialogs.askReason.and.resolveTo('needed');
    api.setPermissions.and.returnValue(of({ ...full, permissions: ['PRODUCT_VIEW', 'PRODUCT_MANAGE'] }));
    q('save-permissions')!.click();
    await fixture.whenStable();
    expect(api.setPermissions).toHaveBeenCalledWith(5, ['PRODUCT_VIEW', 'PRODUCT_MANAGE'], 'needed');
  });

  it('does not save when the reason dialog is cancelled', async () => {
    q('role-ENGINEER')!.click();
    fixture.detectChanges();
    (q('perm-PRODUCT_MANAGE')!.querySelector('input') as HTMLInputElement).click();
    fixture.detectChanges();
    dialogs.askReason.and.resolveTo(null);
    q('save-permissions')!.click();
    await fixture.whenStable();
    expect(api.setPermissions).not.toHaveBeenCalled();
  });
});
