import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/** Business code: upper case letters, digits, dot, underscore, hyphen (DESIGN.md section 5.2). */
export function codePattern(min = 2, max = 30): ValidatorFn {
  return Validators.pattern(new RegExp(`^[A-Z0-9][A-Z0-9._-]{${min - 1},${max - 1}}$`));
}

/** Group validator: {@code confirm} must equal {@code password}. Sets the error on the confirm control. */
export function matchingFields(password: string, confirm: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const confirmControl = group.get(confirm);
    if (!confirmControl) {
      return null;
    }
    const mismatch = group.get(password)?.value !== confirmControl.value;
    const others = { ...(confirmControl.errors ?? {}) };
    delete others['mismatch'];
    if (mismatch) {
      confirmControl.setErrors({ ...others, mismatch: true });
      return { mismatch: true };
    }
    confirmControl.setErrors(Object.keys(others).length ? others : null);
    return null;
  };
}

/** 15-character GSTIN: state code, PAN, entity number, 'Z', check character (DESIGN.md section 5.2). */
export const gstNumber: ValidatorFn = (control) => {
  const value = control.value as string | null;
  return !value || /^\d{2}[A-Z]{5}\d{4}[A-Z]\d[Z][A-Z\d]$/.test(value.trim().toUpperCase())
    ? null
    : { pattern: true, message: 'Not a valid 15-character GST number.' };
};

/** A non-negative number is optional; empty is fine, a negative value is not. */
export const nonNegative: ValidatorFn = (control) => {
  const value = control.value as number | null;
  return value === null || value === undefined || (value as unknown) === '' || value >= 0
    ? null
    : { min: { min: 0, actual: value } };
};
