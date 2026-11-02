import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { ProductFormComponent } from './product-form.component';
import { ProductApi } from './product.api';
import { LookupApi } from '../../../shared/lookup.api';

describe('ProductFormComponent', () => {
  let fixture: ComponentFixture<ProductFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductFormComponent],
      providers: [
        provideNoopAnimations(),
        { provide: MAT_DIALOG_DATA, useValue: null },
        { provide: MatDialogRef, useValue: { close: () => {} } },
        {
          provide: ProductApi,
          useValue: {
            listProductTypes: () => of([]),
            getAttributeDefinitions: () => of([]),
            create: () => of({}),
            update: () => of({}),
            activate: () => of({}),
            getCustomers: () => of([]),
            setCustomers: () => of(undefined),
          },
        },
        {
          provide: LookupApi,
          useValue: {
            workCentres: () => of([]),
            materials: () => of([]),
            customers: () => of([]),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductFormComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });
});
