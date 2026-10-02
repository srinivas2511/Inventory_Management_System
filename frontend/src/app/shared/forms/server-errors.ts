import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup, ValidationErrors } from '@angular/forms';
import { ProblemDetail, isProblemDetail } from '../../core/models/problem.model';

/** The problem body of an HTTP error, if it has the API's shape. */
export function problemOf(error: unknown): ProblemDetail | null {
  return error instanceof HttpErrorResponse && isProblemDetail(error.error) ? error.error : null;
}

/**
 * Puts the server's {@code fieldErrors} onto the matching form controls (by control name; a path such as
 * {@code specifications.legAngle} matches a nested group) and returns the messages that matched no control, for
 * the caller to show in a summary. The error clears as soon as the user edits the field.
 */
export function applyFieldErrors(form: FormGroup, problem: ProblemDetail | null): string[] {
  const unmatched: string[] = [];
  for (const fieldError of problem?.fieldErrors ?? []) {
    const control = findControl(form, fieldError.field);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: fieldError.message });
      control.markAsTouched();
      const clear = control.valueChanges.subscribe(() => {
        clear.unsubscribe();
        if (control.errors?.['server']) {
          const rest = { ...control.errors };
          delete rest['server'];
          control.setErrors(Object.keys(rest).length ? rest : null);
        }
      });
    } else {
      unmatched.push(fieldError.message);
    }
  }
  return unmatched;
}

function findControl(form: FormGroup, path: string): AbstractControl | null {
  return form.get(path.split('.')) ?? null;
}

/** The message to show under a control: a server message first, then the built-in validators. */
export function errorText(control: AbstractControl | null): string {
  const errors: ValidationErrors | null = control?.errors ?? null;
  if (!errors) {
    return '';
  }
  if (typeof errors['server'] === 'string') {
    return errors['server'];
  }
  if (errors['required']) {
    return 'This field is required.';
  }
  if (errors['email']) {
    return 'Enter a valid e-mail address.';
  }
  if (errors['min']) {
    return `Must be at least ${errors['min'].min}.`;
  }
  if (errors['max']) {
    return `Must be at most ${errors['max'].max}.`;
  }
  if (errors['minlength']) {
    return `Must be at least ${errors['minlength'].requiredLength} characters.`;
  }
  if (errors['maxlength']) {
    return `Must be at most ${errors['maxlength'].requiredLength} characters.`;
  }
  if (errors['pattern']) {
    return typeof errors['message'] === 'string' ? errors['message'] : 'The format is not valid.';
  }
  if (errors['mismatch']) {
    return 'The passwords do not match.';
  }
  return 'The value is not valid.';
}
