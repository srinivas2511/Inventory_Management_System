import { MIN_PASSWORD_LENGTH, passwordChecks, passwordMeetsPolicy } from './password-policy';

describe('password policy', () => {
  it('mirrors the server rules: length, upper, lower, digit, special', () => {
    expect(MIN_PASSWORD_LENGTH).toBe(10);
    const labels = passwordChecks('', null).map((c) => c.label);
    expect(labels).toEqual([
      'At least 10 characters',
      'An uppercase letter',
      'A lowercase letter',
      'A digit',
      'A special character',
    ]);
  });

  it('accepts a password that meets every rule', () => {
    expect(passwordMeetsPolicy('Fresh#Start-2026x', 'meena')).toBeTrue();
  });

  it('names each rule that is not met yet', () => {
    const unmet = (pw: string) =>
      passwordChecks(pw, null)
        .filter((c) => !c.ok)
        .map((c) => c.label);
    expect(unmet('Short#1a')).toEqual(['At least 10 characters']);
    expect(unmet('alllowercase1#')).toEqual(['An uppercase letter']);
    expect(unmet('ALLUPPERCASE1#')).toEqual(['A lowercase letter']);
    expect(unmet('NoDigitsHere#x')).toEqual(['A digit']);
    expect(unmet('NoSpecial12345x')).toEqual(['A special character']);
  });

  it('adds the username rule only when there is a username, and ignores case', () => {
    expect(passwordChecks('Whatever#12345', null).length).toBe(5);
    const checks = passwordChecks('Meena#Pass-2026', 'MEENA');
    expect(checks.length).toBe(6);
    expect(checks[5]).toEqual({ label: 'Does not contain the username', ok: false });
    expect(passwordMeetsPolicy('Meena#Pass-2026', 'meena')).toBeFalse();
  });
});
