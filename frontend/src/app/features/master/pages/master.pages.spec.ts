import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { SessionStore } from '../../../core/auth/session.store';
import { ToastService } from '../../../core/notification/toast.service';
import { DialogService } from '../../../shared/dialogs/dialog.service';
import { MaterialsApi } from '../api/materials.api';
import { ProductsApi } from '../api/products.api';
import { MaterialSummary, ProductSummary } from '../models';
import { MaterialsPageComponent } from './materials-page.component';
import { PartnersPageComponent } from './partners-page.component';
import { ProductsPageComponent } from './products-page.component';

const page = <T>(content: T[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });
const permissions = signal(new Set<string>());
const query = (fixture: ComponentFixture<unknown>, id: string) =>
  fixture.nativeElement.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;

function configure(providers: unknown[]) {
  permissions.set(new Set());
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideNoopAnimations(),
      { provide: SessionStore, useValue: { permissions } },
      { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error', 'info']) },
      ...(providers as never[]),
    ],
  });
}

describe('MaterialsPageComponent', () => {
  const material: MaterialSummary = {
    id: 1,
    code: 'RM-1',
    name: 'Wire',
    materialType: 'STAINLESS',
    uom: 'KG',
    active: true,
  };
  let api: jasmine.SpyObj<MaterialsApi>;
  let dialogs: jasmine.SpyObj<DialogService>;
  let fixture: ComponentFixture<MaterialsPageComponent>;

  beforeEach(async () => {
    api = jasmine.createSpyObj<MaterialsApi>('MaterialsApi', ['list', 'get', 'activate', 'deactivate']);
    api.list.and.returnValue(of(page([material])));
    dialogs = jasmine.createSpyObj<DialogService>('DialogService', ['askReason', 'confirm']);
    configure([
      { provide: MaterialsApi, useValue: api },
      { provide: DialogService, useValue: dialogs },
    ]);
    fixture = TestBed.createComponent(MaterialsPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('lists materials with the default sort', () => {
    expect(api.list).toHaveBeenCalledWith(jasmine.objectContaining({ page: 0, sort: 'code,asc' }));
    expect(fixture.nativeElement.textContent).toContain('RM-1');
  });

  it('offers "New material" and row actions only to those who may manage master data', () => {
    expect(query(fixture, 'new')).toBeNull();
    expect(query(fixture, 'row-menu')).toBeNull();
    permissions.set(new Set(['MASTERDATA_MANAGE']));
    fixture.detectChanges();
    expect(query(fixture, 'new')).not.toBeNull();
    expect(query(fixture, 'row-menu')).not.toBeNull();
  });

  it('asks before forcing the deactivation of a material that is in use', async () => {
    dialogs.askReason.and.resolveTo('obsolete grade');
    dialogs.confirm.and.resolveTo(true);
    api.deactivate.and.returnValues(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { code: 'DEACTIVATION_BLOCKED', status: 422, detail: 'Used by 2 products.' },
          }),
      ),
      of(undefined),
    );
    await (fixture.componentInstance as unknown as { deactivate(m: MaterialSummary): Promise<void> }).deactivate(
      material,
    );
    await fixture.whenStable();
    expect(dialogs.confirm).toHaveBeenCalledWith(
      jasmine.objectContaining({ message: jasmine.stringContaining('Used by 2 products.') }),
    );
    expect(api.deactivate.calls.allArgs()).toEqual([
      [1, false, 'obsolete grade'],
      [1, true, 'obsolete grade'],
    ]);
  });

  it('does not force when the user declines', async () => {
    dialogs.askReason.and.resolveTo('');
    dialogs.confirm.and.resolveTo(false);
    api.deactivate.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 422, error: { code: 'DEACTIVATION_BLOCKED', status: 422 } })),
    );
    await (fixture.componentInstance as unknown as { deactivate(m: MaterialSummary): Promise<void> }).deactivate(
      material,
    );
    await fixture.whenStable();
    expect(api.deactivate).toHaveBeenCalledTimes(1);
  });
});

describe('PartnersPageComponent', () => {
  it('uses the supplier or customer API and write permission by kind', () => {
    const calls: string[] = [];
    configure([
      {
        provide: ApiService,
        useValue: { get: (path: string) => (calls.push(path), of(page([]))) },
      },
    ]);
    const suppliers = TestBed.createComponent(PartnersPageComponent);
    suppliers.componentRef.setInput('kind', 'suppliers');
    suppliers.detectChanges();
    expect(calls).toEqual(['/suppliers']);
    expect(suppliers.nativeElement.querySelector('h1').textContent).toContain('Suppliers');
    expect(query(suppliers, 'new')).toBeNull();
    permissions.set(new Set(['SUPPLIER_MANAGE']));
    suppliers.detectChanges();
    expect(query(suppliers, 'new')).not.toBeNull();

    const customers = TestBed.createComponent(PartnersPageComponent);
    customers.componentRef.setInput('kind', 'customers');
    customers.detectChanges();
    expect(calls).toEqual(['/suppliers', '/customers']);
    expect(query(customers, 'new')).toBeNull(); // holds SUPPLIER_MANAGE, not CUSTOMER_MANAGE
  });
});

describe('ProductsPageComponent', () => {
  const row = (
    id: number,
    status: ProductSummary['status'],
    allowedActions: ProductSummary['allowedActions'],
  ): ProductSummary => ({
    id,
    code: `SPR-${id}`,
    name: 'Spring',
    springType: 'COMPRESSION',
    status,
    active: status === 'ACTIVE',
    allowedActions,
  });
  let api: jasmine.SpyObj<ProductsApi>;
  let fixture: ComponentFixture<ProductsPageComponent>;

  beforeEach(async () => {
    api = jasmine.createSpyObj<ProductsApi>('ProductsApi', ['list', 'activate', 'obsolete']);
    api.list.and.returnValue(
      of(page([row(1, 'DRAFT', ['EDIT', 'ACTIVATE']), row(2, 'ACTIVE', []), row(3, 'OBSOLETE', ['ACTIVATE'])])),
    );
    configure([
      { provide: ProductsApi, useValue: api },
      { provide: DialogService, useValue: jasmine.createSpyObj('DialogService', ['askReason']) },
    ]);
    fixture = TestBed.createComponent(ProductsPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('shows a row menu only where the server allows an action', () => {
    expect(fixture.nativeElement.querySelectorAll('[data-testid="row"]').length).toBe(3);
    expect(fixture.nativeElement.querySelectorAll('[data-testid="row-menu"]').length).toBe(2);
  });

  it('hides "New product" without PRODUCT_CREATE', () => {
    expect(query(fixture, 'new')).toBeNull();
    permissions.set(new Set(['PRODUCT_CREATE']));
    fixture.detectChanges();
    expect(query(fixture, 'new')).not.toBeNull();
  });

  it('lists the missing attributes when activation is refused', () => {
    const toast = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;
    api.activate.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              code: 'VALIDATION_FAILED',
              status: 400,
              fieldErrors: [{ field: 'wireDiameter', message: 'Wire diameter is required.' }],
            },
          }),
      ),
    );
    (fixture.componentInstance as unknown as { activate(p: ProductSummary): void }).activate(
      row(1, 'DRAFT', ['ACTIVATE']),
    );
    expect(toast.error).toHaveBeenCalledWith(jasmine.stringContaining('Wire diameter is required.'));
  });

  it('opens the edit page for a product', () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);
    (fixture.componentInstance as unknown as { edit(p: ProductSummary): void }).edit(row(1, 'DRAFT', ['EDIT']));
    expect(navigate).toHaveBeenCalledWith(['/master/products', 1, 'edit']);
  });
});
