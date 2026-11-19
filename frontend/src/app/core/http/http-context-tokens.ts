import { HttpContextToken } from '@angular/common/http';

/** Set to true on a request to prevent the error interceptor from showing a toast on failure. */
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);
