import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { ToastService } from '../../../core/notification/toast.service';
import { LookupItem, LookupPickerComponent } from '../../../shared/ui/lookup-picker.component';
import { PartnersApi } from '../api/partners.api';
import { PartnerFormDialogComponent } from './partner-form-dialog.component';

describe('PartnerFormDialogComponent', () => {
  let fixture: ComponentFixture<PartnerFormDialogComponent>;
  let api: jasmine.SpyObj<PartnersApi>;
  let ref: jasmine.SpyObj<MatDialogRef<unknown>>;

  const el = (id: string) => fixture.nativeElement.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const type = (id: string, value: string) => {
    const input = el(id) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  beforeEach(async () => {
    api = jasmine.createSpyObj<PartnersApi>('PartnersApi', ['create', 'update']);
    ref = jasmine.createSpyObj('MatDialogRef', ['close']);
    await TestBed.configureTestingModule({
      imports: [PartnerFormDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: MAT_DIALOG_DATA, useValue: { api, noun: 'Supplier' } },
        { provide: MatDialogRef, useValue: ref },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error', 'info']) },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PartnerFormDialogComponent);
    fixture.detectChanges();
  });

  it('rejects an invalid code and GST number without calling the server', () => {
    type('code', 'bad code');
    type('name', 'Sundaram Wires');
    type('gstNumber', '123');
    (el('save') as HTMLButtonElement).click();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('creates a supplier with a trimmed, upper-case GST number', () => {
    api.create.and.returnValue(of({ id: 1, code: 'SUP-1', name: 'Sundaram Wires', active: true, version: 0 }));
    type('code', 'SUP-1');
    type('name', ' Sundaram Wires ');
    type('gstNumber', '33aabcs1234a1z5');
    (el('save') as HTMLButtonElement).click();
    expect(api.create).toHaveBeenCalledWith(
      jasmine.objectContaining({ code: 'SUP-1', name: 'Sundaram Wires', gstNumber: '33AABCS1234A1Z5' }),
    );
    expect(ref.close).toHaveBeenCalled();
  });

  it('shows a duplicate-code error from the server on the code field', () => {
    api.create.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'DUPLICATE_KEY', status: 409, detail: "Supplier code 'SUP-1' already exists." },
          }),
      ),
    );
    type('code', 'SUP-1');
    type('name', 'X');
    (el('save') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el('form-error')?.textContent).toContain('already exists');
    expect(ref.close).not.toHaveBeenCalled();
  });
});

@Component({
  standalone: true,
  imports: [LookupPickerComponent, ReactiveFormsModule],
  template: `<app-lookup-picker label="Customer" [search]="search" [formControl]="control" />`,
})
class PickerHostComponent {
  readonly control = new FormControl<LookupItem | null>(null);
  readonly queries: string[] = [];
  readonly search = (q: string) => {
    this.queries.push(q);
    return of([{ id: 1, code: 'C1', name: 'ABC Automotive' }]);
  };
}

describe('LookupPickerComponent', () => {
  it('searches as the user types, and shows an existing value', fakeAsync(() => {
    TestBed.configureTestingModule({ imports: [PickerHostComponent], providers: [provideNoopAnimations()] });
    const fixture = TestBed.createComponent(PickerHostComponent);
    fixture.componentInstance.control.setValue({ id: 5, code: 'C5', name: 'Existing' });
    fixture.detectChanges();
    flushMicrotasks();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.value).toBe('C5 — Existing');
    input.value = 'abc';
    input.dispatchEvent(new Event('input'));
    tick(300);
    expect(fixture.componentInstance.queries).toEqual(['abc']);
  }));

  it('clears the value when the text is cleared', fakeAsync(() => {
    TestBed.configureTestingModule({ imports: [PickerHostComponent], providers: [provideNoopAnimations()] });
    const fixture = TestBed.createComponent(PickerHostComponent);
    fixture.componentInstance.control.setValue({ id: 5, code: 'C5', name: 'Existing' });
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.value = '';
    input.dispatchEvent(new Event('input'));
    tick(300);
    expect(fixture.componentInstance.control.value).toBeNull();
  }));
});
