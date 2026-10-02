import { FormControl, FormGroup } from '@angular/forms';
import { AttributeDefinition } from '../../features/master/models';
import { collectAttributeValues, enforceRequired, syncAttributeControls } from './dynamic-spec';

const def = (over: Partial<AttributeDefinition>): AttributeDefinition => ({
  code: 'x',
  label: 'X',
  dataType: 'NUMBER',
  required: false,
  enumValues: [],
  displayOrder: 10,
  storage: 'CORE',
  ...over,
});

const wire = def({ code: 'wireDiameter', required: true, minValue: 0.001 });
const angle = def({ code: 'legAngle', storage: 'SPECIFICATIONS', maxValue: 360, required: true });
const hook = def({ code: 'hookType', dataType: 'ENUM', enumValues: ['A', 'B'], storage: 'SPECIFICATIONS' });

function newForm(): FormGroup {
  return new FormGroup({ name: new FormControl(''), specifications: new FormGroup({}) });
}

describe('dynamic spec form helpers', () => {
  it('puts core attributes at the top and the rest under specifications', () => {
    const form = newForm();
    syncAttributeControls(form, [], [wire, angle]);
    expect(form.get('wireDiameter')).not.toBeNull();
    expect(form.get('specifications.legAngle')).not.toBeNull();
  });

  it('replaces the controls of the previous type and keeps unrelated ones', () => {
    const form = newForm();
    const first = syncAttributeControls(form, [], [wire, angle]);
    syncAttributeControls(form, first, [hook]);
    expect(form.get('wireDiameter')).toBeNull();
    expect(form.get('specifications.legAngle')).toBeNull();
    expect(form.get('specifications.hookType')).not.toBeNull();
    expect(form.get('name')).not.toBeNull();
  });

  it('seeds initial values', () => {
    const form = newForm();
    syncAttributeControls(form, [], [wire], () => 2.5);
    expect(form.get('wireDiameter')!.value).toBe(2.5);
  });

  it('applies numeric limits always and required only when enforced', () => {
    const form = newForm();
    const attrs = syncAttributeControls(form, [], [wire, angle]);
    form.get('specifications.legAngle')!.setValue(400);
    expect(form.get('specifications.legAngle')!.errors?.['max']).toBeDefined();
    expect(form.get('wireDiameter')!.valid).toBeTrue(); // draft: empty is fine
    enforceRequired(form, attrs, true);
    expect(form.get('wireDiameter')!.errors?.['required']).toBeTrue();
    enforceRequired(form, attrs, false);
    expect(form.get('wireDiameter')!.valid).toBeTrue();
  });

  it('collects core values (null when empty) and non-empty specifications', () => {
    const form = newForm();
    const attrs = syncAttributeControls(form, [], [wire, angle, hook]);
    form.get('wireDiameter')!.setValue(2.5);
    form.get('specifications.hookType')!.setValue('A');
    expect(collectAttributeValues(form, attrs)).toEqual({
      core: { wireDiameter: 2.5 },
      specifications: { hookType: 'A' },
    });
  });
});
