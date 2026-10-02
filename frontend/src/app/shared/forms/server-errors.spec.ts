import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { applyFieldErrors, errorText, problemOf } from './server-errors';
import { matchingFields } from './validators';

describe('server errors', () => {
  it('reads a problem body from an HTTP error only', () => {
    const problem = { code: 'X', status: 400 };
    expect(problemOf(new HttpErrorResponse({ error: problem, status: 400 }))).toEqual(problem);
    expect(problemOf(new HttpErrorResponse({ error: 'oops', status: 500 }))).toBeNull();
    expect(problemOf(new Error('x'))).toBeNull();
  });

  it('applies field errors to controls, clears them on edit, and returns unmatched messages', () => {
    const form = new FormGroup({
      name: new FormControl(''),
      nested: new FormGroup({ angle: new FormControl('') }),
    });
    const unmatched = applyFieldErrors(form, {
      code: 'VALIDATION_FAILED',
      status: 400,
      fieldErrors: [
        { field: 'name', message: 'taken' },
        { field: 'nested.angle', message: 'too big' },
        { field: 'other', message: 'nowhere' },
      ],
    });
    expect(unmatched).toEqual(['nowhere']);
    expect(errorText(form.get('name'))).toBe('taken');
    expect(errorText(form.get('nested.angle'))).toBe('too big');
    form.get('name')!.setValue('x');
    expect(form.get('name')!.errors).toBeNull();
  });

  it('describes built-in validator errors', () => {
    const c = new FormControl('', Validators.required);
    expect(errorText(c)).toBe('This field is required.');
    expect(errorText(new FormControl('a', Validators.email))).toBe('Enter a valid e-mail address.');
    expect(errorText(new FormControl('a'))).toBe('');
  });

  it('flags mismatching passwords on the confirm control', () => {
    const form = new FormGroup(
      { password: new FormControl('abc'), confirm: new FormControl('abd') },
      { validators: matchingFields('password', 'confirm') },
    );
    expect(form.get('confirm')!.errors?.['mismatch']).toBeTrue();
    form.get('confirm')!.setValue('abc');
    expect(form.get('confirm')!.errors).toBeNull();
  });
});
