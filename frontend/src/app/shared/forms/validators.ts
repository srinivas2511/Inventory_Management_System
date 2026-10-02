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
