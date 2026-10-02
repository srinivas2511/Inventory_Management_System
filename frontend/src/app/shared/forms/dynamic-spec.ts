import { AbstractControl, FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { AttributeDefinition } from '../../features/master/models';

/** Where an attribute's control lives in the product form: a core field at the top, others under {@code specifications}. */
export function attributePath(attribute: Pick<AttributeDefinition, 'code' | 'storage'>): string[] {
  return attribute.storage === 'CORE' ? [attribute.code] : ['specifications', attribute.code];
}

function validatorsOf(attribute: AttributeDefinition, enforceRequired: boolean): ValidatorFn[] {
  const validators: ValidatorFn[] = [];
  if (attribute.required && enforceRequired) {
    validators.push(Validators.required);
  }
  if (attribute.dataType === 'NUMBER') {
    if (attribute.minValue !== null && attribute.minValue !== undefined) {
      validators.push(Validators.min(attribute.minValue));
    }
    if (attribute.maxValue !== null && attribute.maxValue !== undefined) {
      validators.push(Validators.max(attribute.maxValue));
    }
  }
  return validators;
}

/**
 * Adds a control per attribute definition to the product form (and removes the ones of a previous spring type),
 * returning the attributes now in the form. Values already held by controls of the same name are kept when the
 * definition still exists, so editing a product or switching and switching back does not lose input.
 */
export function syncAttributeControls(
  form: FormGroup,
  previous: AttributeDefinition[],
  next: AttributeDefinition[],
  initial: (attribute: AttributeDefinition) => unknown = () => null,
): AttributeDefinition[] {
  const specs = form.get('specifications') as FormGroup;
  for (const old of previous) {
    const path = attributePath(old);
    const parent = path.length === 1 ? form : specs;
    parent.removeControl(path[path.length - 1]);
  }
  for (const attribute of next) {
    const path = attributePath(attribute);
    const parent = path.length === 1 ? form : specs;
    parent.addControl(
      path[path.length - 1],
      new FormControl<unknown>(initial(attribute) ?? null, validatorsOf(attribute, false)),
    );
  }
  return next;
}

/** Turns the required flags on (to activate) or off (to save a draft) for every attribute control. */
export function enforceRequired(form: FormGroup, attributes: AttributeDefinition[], enforce: boolean): void {
  for (const attribute of attributes) {
    const control = form.get(attributePath(attribute));
    control?.setValidators(validatorsOf(attribute, enforce));
    control?.updateValueAndValidity({ emitEvent: false });
  }
}

/** The values of the attribute controls split into core fields and the specifications map, empty ones omitted. */
export function collectAttributeValues(
  form: FormGroup,
  attributes: AttributeDefinition[],
): { core: Record<string, unknown>; specifications: Record<string, unknown> } {
  const core: Record<string, unknown> = {};
  const specifications: Record<string, unknown> = {};
  for (const attribute of attributes) {
    const control: AbstractControl | null = form.get(attributePath(attribute));
    const value = control?.value;
    const empty = value === null || value === undefined || value === '';
    if (attribute.storage === 'CORE') {
      core[attribute.code] = empty ? null : value;
    } else if (!empty) {
      specifications[attribute.code] = value;
    }
  }
  return { core, specifications };
}
