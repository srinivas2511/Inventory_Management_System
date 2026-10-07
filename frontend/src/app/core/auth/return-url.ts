const AUTH_PAGES = ['/login', '/forgot-password', '/reset-password'];

/**
 * A "return to where you were" URL is only followed when it is an in-app path. Anything that could leave the app
 * (another origin, a protocol-relative `//host`, a backslash trick, control characters) is dropped, and so are the
 * sign-in pages themselves, which would loop.
 */
export function safeReturnUrl(raw: string | null | undefined): string | null {
  if (!raw || raw.length > 500) {
    return null;
  }
  if (!raw.startsWith('/') || raw.startsWith('//') || raw.includes('\\')) {
    return null;
  }
  for (let i = 0; i < raw.length; i++) {
    if (raw.charCodeAt(i) < 32) {
      return null;
    }
  }
  const path = raw.split(/[?#]/)[0];
  if (path === '/' || AUTH_PAGES.some((p) => path === p || path.startsWith(`${p}/`))) {
    return null;
  }
  return raw;
}
