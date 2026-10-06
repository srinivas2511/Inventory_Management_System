import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { AttributeDefinitionAdminDto, AttributeDefinitionsComponent } from './attribute-definitions.component';

function def(over: Partial<AttributeDefinitionAdminDto>): AttributeDefinitionAdminDto {
  return {
    id: 1,
    productType: 'AUTOMOTIVE_PART',
    attributeCode: 'thread_pitch',
    label: 'Thread Pitch',
    dataType: 'NUMBER',
    unit: 'mm',
    required: true,
    minValue: 0,
    maxValue: null,
    enumValues: [],
    displayOrder: 1,
    active: true,
    ...over,
  };
}

const types = [
  { code: 'COMPRESSION', name: 'Compression spring' },
  { code: 'AUTOMOTIVE_PART', name: 'Automotive part' },
];

describe('AttributeDefinitionsComponent', () => {
  let fixture: ComponentFixture<AttributeDefinitionsComponent>;
  let http: HttpTestingController;

  async function create(rows: AttributeDefinitionAdminDto[]): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AttributeDefinitionsComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AttributeDefinitionsComponent);
    fixture.detectChanges();
    http.expectOne('/api/product-types').flush(types);
    http.expectOne((r) => r.url === '/api/admin/attribute-definitions').flush(rows);
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  it('lists the definitions with their type, code, status and enum values', async () => {
    await create([
      def({}),
      def({
        id: 2,
        attributeCode: 'finish_type',
        label: 'Finish',
        dataType: 'ENUM',
        unit: null,
        enumValues: ['MATTE', 'GLOSS'],
        active: false,
      }),
    ]);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('thread_pitch');
    expect(text).toContain('Thread Pitch (mm)');
    expect(text).toContain('ENUM: MATTE, GLOSS');
    expect(text).toContain('Inactive');
    expect(fixture.nativeElement.querySelectorAll('tr.mat-mdc-row').length).toBe(2);
  });

  it('creates an attribute, sending the enum values as a list and the limits only for numbers', async () => {
    await create([]);
    const cmp = fixture.componentInstance as unknown as {
      startCreate(): void;
      save(): void;
      form: {
        patchValue(v: object): void;
      };
    };
    cmp.startCreate();
    cmp.form.patchValue({
      productType: 'AUTOMOTIVE_PART',
      attributeCode: 'surface_finish',
      label: ' Surface Finish ',
      dataType: 'ENUM',
      enumValues: 'MATTE, GLOSS ,, ',
      minValue: 5,
      displayOrder: 3,
    });
    cmp.save();

    const post = http.expectOne('/api/admin/attribute-definitions');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({
      productType: 'AUTOMOTIVE_PART',
      attributeCode: 'surface_finish',
      label: 'Surface Finish',
      dataType: 'ENUM',
      unit: null,
      required: false,
      minValue: null,
      maxValue: null,
      enumValues: ['MATTE', 'GLOSS'],
      displayOrder: 3,
    });
    post.flush(def({ id: 9, attributeCode: 'surface_finish' }));
    http.expectOne((r) => r.url === '/api/admin/attribute-definitions').flush([]);
  });

  it('refuses a code that is not lower snake case', async () => {
    await create([]);
    const cmp = fixture.componentInstance as unknown as {
      startCreate(): void;
      form: { patchValue(v: object): void; controls: { attributeCode: { valid: boolean } } };
    };
    cmp.startCreate();
    cmp.form.patchValue({ attributeCode: 'Bad-Code' });
    expect(cmp.form.controls.attributeCode.valid).toBe(false);
    cmp.form.patchValue({ attributeCode: 'good_code' });
    expect(cmp.form.controls.attributeCode.valid).toBe(true);
  });

  it('edits an existing attribute with PUT and keeps its type and code', async () => {
    await create([def({})]);
    const cmp = fixture.componentInstance as unknown as {
      startEdit(d: AttributeDefinitionAdminDto): void;
      save(): void;
      form: { patchValue(v: object): void };
    };
    cmp.startEdit(def({}));
    cmp.form.patchValue({ label: 'Pitch' });
    cmp.save();

    const put = http.expectOne('/api/admin/attribute-definitions/1');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.productType).toBe('AUTOMOTIVE_PART');
    expect(put.request.body.attributeCode).toBe('thread_pitch');
    expect(put.request.body.label).toBe('Pitch');
    put.flush(def({ label: 'Pitch' }));
    http.expectOne((r) => r.url === '/api/admin/attribute-definitions').flush([]);
  });

  it('deactivates an active attribute and activates an inactive one', async () => {
    const active = def({});
    const inactive = def({ id: 2, attributeCode: 'old_attr', active: false });
    await create([active, inactive]);
    const cmp = fixture.componentInstance as unknown as {
      toggle(d: AttributeDefinitionAdminDto): void;
    };

    cmp.toggle(active);
    const off = http.expectOne('/api/admin/attribute-definitions/1/deactivate');
    expect(off.request.method).toBe('POST');
    off.flush(def({ active: false }));
    http.expectOne((r) => r.url === '/api/admin/attribute-definitions').flush([]);

    cmp.toggle(inactive);
    http.expectOne('/api/admin/attribute-definitions/2/activate').flush(def({ id: 2 }));
    http.expectOne((r) => r.url === '/api/admin/attribute-definitions').flush([]);
  });

  it('filters by product type', async () => {
    await create([]);
    const cmp = fixture.componentInstance as unknown as { setFilter(t: string): void };
    cmp.setFilter('COMPRESSION');
    const req = http.expectOne((r) => r.url === '/api/admin/attribute-definitions');
    expect(req.request.params.get('productType')).toBe('COMPRESSION');
    req.flush([]);
  });
});
