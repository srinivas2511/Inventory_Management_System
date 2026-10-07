/** The same complexity rules the server enforces (AuthService.validateComplexity), shown while the user types. */
export const MIN_PASSWORD_LENGTH = 10;

export interface PolicyCheck {
  label: string;
  ok: boolean;
}

export function passwordChecks(password: string, username: string | null): PolicyCheck[] {
  const checks: PolicyCheck[] = [
    { label: `At least ${MIN_PASSWORD_LENGTH} characters`, ok: password.length >= MIN_PASSWORD_LENGTH },
    { label: 'An uppercase letter', ok: /[A-Z]/.test(password) },
    { label: 'A lowercase letter', ok: /[a-z]/.test(password) },
    { label: 'A digit', ok: /\d/.test(password) },
    { label: 'A special character', ok: /[^A-Za-z0-9]/.test(password) },
  ];
  if (username) {
    checks.push({
      label: 'Does not contain the username',
      ok: !password.toLowerCase().includes(username.toLowerCase()),
    });
  }
  return checks;
}

export function passwordMeetsPolicy(password: string, username: string | null): boolean {
  return passwordChecks(password, username).every((c) => c.ok);
}
