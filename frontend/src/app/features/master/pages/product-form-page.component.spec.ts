import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ToastService } from '../../../core/notification/toast.service';
import { DialogService } from '../../../shared/dialogs/dialog.service';
import { LookupsApi } from '../api/lookups.api';
import { ProductsApi } from '../api/products.api';
import { AttributeDefinition, ProductResponse } from '../models';
import { ProductFormPageComponent } from './product-form-page.component';

const attr = (over: Partial<AttributeDefinition>): AttributeDefinition => ({
  code: 'x',
  label: 'X',
  dataType: 'NUMBER',
  required: false,
  enumValues: [],
  displayOrder: 10,
  storage: 'CORE',
  ...over,
});

const compression = [
  attr({ code: 'wireDiameter', label: 'Wire diameter', required: true, unit: 'mm' }),
  attr({ code: 'freeLength', label: 'Free length', required: true, displayOrder: 20 }),
  attr({
    code: 'coilDirection',
    label: 'Coil direction',
    dataType: 'ENUM',
    enumValues: ['RIGHT', 'LEFT'],
    storage: 'SPECIFICATIONS',
    displayOrder: 30,
  }),
];
const torsion = [attr({ code: 'legAngle', label: 'Leg angle', required: true, storage: 'SPECIFICATIONS' })];

describe('ProductFormPageComponent', () => {
  let api: jasmine.SpyObj<ProductsApi>;
  let dialogs: jasmine.SpyObj<DialogService>;
  let router: Router;
  let fixture: ComponentFixture<ProductFormPageComponent>;

  const el = (id: string) => fixture.nativeElement.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const type = (id: string, value: string) => {
    const input = el(id) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const cmp = () =>
    fixture.componentInstance as unknown as { submit(activate: boolean): void; typeChanged(t: string): Promise<void> };

  async function create(id?: string, product?: ProductResponse) {
    api = jasmine.createSpyObj<ProductsApi>('ProductsApi', [
      'springTypes',
      'attributes',
      'get',
      'create',
      'update',
      'activate',
    ]);
    api.springTypes.and.returnValue(of([]));
    api.get.and.returnValue(of(product as ProductResponse));
    api.attributes.and.callFake((t) => of(t === 'TORSION' ? torsion : t === 'COMPRESSION' ? compression : []));
    dialogs = jasmine.createSpyObj<DialogService>('DialogService', ['confirm']);
    await TestBed.configureTestingModule({
      imports: [ProductFormPageComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ProductsApi, useValue: api },
        { provide: DialogService, useValue: dialogs },
        { provide: LookupsApi, useValue: { customers: () => of([]), materialsMatching: () => of([]) } },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error', 'info']) },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(ProductFormPageComponent);
    if (id) {
      fixture.componentRef.setInput('id', id);
    }
    fixture.detectChanges();
  }

  const saved = { id: 7, code: 'SPR-7', version: 0 } as ProductResponse;

  it('renders the attribute inputs of the default spring type', async () => {
    await create();
    expect(api.attributes).toHaveBeenCalledWith('COMPRESSION');
    expect(el('attr-wireDiameter')).not.toBeNull();
    expect(el('attr-coilDirection')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Wire diameter *'); // required marker
  });

  it('saves a draft without the required attributes, core values on top and the rest as specifications', async () => {
    await create();
    api.create.and.returnValue(of(saved));
    type('code', 'SPR-7');
    type('name', 'Door spring');
    type('attr-wireDiameter', '2.5');
    cmp().submit(false);
    expect(api.create).toHaveBeenCalledWith(
      jasmine.objectContaining({
        productCode: 'SPR-7',
        name: 'Door spring',
        springType: 'COMPRESSION',
        wireDiameter: 2.5,
        freeLength: null,
        specifications: {},
      }),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/master/products']);
  });

  it('does not activate until every required attribute is filled', async () => {
    await create();
    type('code', 'SPR-7');
    type('name', 'Door spring');
    cmp().submit(true);
    fixture.detectChanges();
    expect(api.create).not.toHaveBeenCalled();
    expect(el('form-error')?.textContent).toContain('required');
  });

  it('creates and then activates', async () => {
    await create();
    api.create.and.returnValue(of(saved));
    api.activate.and.returnValue(of({ ...saved, status: 'ACTIVE' } as ProductResponse));
    type('code', 'SPR-7');
    type('name', 'Door spring');
    type('attr-wireDiameter', '2.5');
    type('attr-freeLength', '50');
    cmp().submit(true);
    expect(api.create).toHaveBeenCalled();
    expect(api.activate).toHaveBeenCalledWith(7);
  });

  it('keeps the draft and shows the server message when activation is refused', async () => {
    await create();
    api.create.and.returnValue(of(saved));
    api.activate.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { code: 'VALIDATION_FAILED', status: 400, detail: 'Missing required attributes.' },
          }),
      ),
    );
    type('code', 'SPR-7');
    type('name', 'Door spring');
    type('attr-wireDiameter', '2.5');
    type('attr-freeLength', '50');
    cmp().submit(true);
    fixture.detectChanges();
    expect(router.navigate).toHaveBeenCalledWith(['/master/products', 7, 'edit'], { replaceUrl: true });
    expect(el('form-error')?.textContent).toContain('Missing required attributes.');
  });

  it('puts server field errors on the matching control', async () => {
    await create();
    api.create.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              code: 'VALIDATION_FAILED',
              status: 400,
              fieldErrors: [{ field: 'productCode', message: 'Code already exists.' }],
            },
          }),
      ),
    );
    type('code', 'SPR-7');
    type('name', 'Door spring');
    cmp().submit(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Code already exists.');
  });

  it('asks before clearing filled values when the spring type changes, and keeps the old type on "no"', async () => {
    await create();
    type('attr-wireDiameter', '2.5');
    dialogs.confirm.and.resolveTo(false);
    await cmp().typeChanged('TORSION');
    expect(dialogs.confirm).toHaveBeenCalled();
    expect(api.attributes).not.toHaveBeenCalledWith('TORSION');
    expect(el('attr-wireDiameter')).not.toBeNull();

    dialogs.confirm.and.resolveTo(true);
    await cmp().typeChanged('TORSION');
    fixture.detectChanges();
    expect(el('attr-legAngle')).not.toBeNull();
    expect(el('attr-wireDiameter')).toBeNull();
  });

  it('changes the type without asking when nothing was entered', async () => {
    await create();
    await cmp().typeChanged('TORSION');
    expect(dialogs.confirm).not.toHaveBeenCalled();
    expect(api.attributes).toHaveBeenCalledWith('TORSION');
  });

  describe('editing', () => {
    const existing = {
      id: 7,
      code: 'SPR-7',
      name: 'Door spring',
      springType: 'COMPRESSION',
      status: 'DRAFT',
      active: false,
      wireDiameter: 2.5,
      freeLength: 50,
      innerDiameter: 18, // a column the compression catalogue does not describe
      specifications: { coilDirection: 'LEFT' },
      allowedActions: ['EDIT', 'ACTIVATE'],
      version: 4,
    } as unknown as ProductResponse;

    it('loads the product into the form, fixes the code, and keeps unseen columns on save', async () => {
      await create('7', existing);
      fixture.detectChanges();
      api.update.and.returnValue(of(existing));
      cmp().submit(false);
      expect(api.update).toHaveBeenCalledWith(
        7,
        jasmine.objectContaining({
          productCode: 'SPR-7',
          wireDiameter: 2.5,
          innerDiameter: 18,
          specifications: { coilDirection: 'LEFT' },
          version: 4,
        }),
      );
    });

    it('fixes the spring type of a product that is no longer a draft', async () => {
      await create('7', { ...existing, status: 'ACTIVE', allowedActions: ['EDIT', 'OBSOLETE'] } as ProductResponse);
      fixture.detectChanges();
      expect(fixture.componentInstance['form'].controls.springType.disabled).toBeTrue();
      api.update.and.returnValue(of(existing));
      cmp().submit(false);
      expect(api.update).toHaveBeenCalledWith(7, jasmine.objectContaining({ springType: 'COMPRESSION' }));
    });

    it('is read-only when the server allows no edit', async () => {
      await create('7', { ...existing, status: 'OBSOLETE', allowedActions: ['ACTIVATE'] } as ProductResponse);
      fixture.detectChanges();
      expect(el('read-only')).not.toBeNull();
      expect(el('save')).toBeNull();
    });
  });
});
