# E2E Defects and Fixes

**Date:** 2026-10-27  
**Session:** Chrome browser automation

---

## Defects Found

### E2E-D-01: Document Upload — Click at Wrong Screen Coordinates
**Severity:** LOW  
**Screen:** `/documents/new`  
**Description:** When using coordinate-based clicks on a 0.5-scale screenshot, the button coordinates need to be multiplied by 2 for the actual 1536x673 viewport. Clicking at raw screenshot pixel coordinates (172, 319) hit the Materials navigation link rather than the Upload button.  
**Root Cause:** The coordinate frame for the tool is always the full-resolution frame (1536x673), but the displayed image is at 0.5 scale (768x337). Button appeared at (172, 319) in the image but is at (344, 638) in the full-res frame.  
**Fix Applied:** Used element reference (`ref_26`) from `read_page` instead of pixel coordinates.  
**Status:** ✅ Fixed

### E2E-D-02: Document Upload — PDF MIME Validation
**Severity:** LOW (correct behavior, misleading error message)  
**Screen:** `/documents/new`  
**Description:** Creating a `File` object with `{type: 'application/pdf'}` from a plain-text blob still fails server validation with HTTP 415 "The file content does not match its .pdf extension". The server inspects actual file content (magic bytes), not just the Content-Type header.  
**Root Cause:** Server uses content inspection (magic bytes) for MIME validation, not just the declared Content-Type.  
**Fix Applied:** Used proper PDF header bytes (`%PDF-1.4\n1 0 obj...`) to create a minimal valid PDF.  
**Status:** ✅ Fixed  
**Note:** This is correct, secure behavior — the server correctly rejects disguised files.

### E2E-D-03: WMS Transfer Form UX — Numeric IDs Required
**Severity:** INFO  
**Screen:** `/wms/tasks` (Transfer Orders)  
**Description:** The WMS transfer form requires users to enter raw numeric database IDs for `itemId`, `batchId`, `fromBinId`, `toBinId`. There are no lookup controls or autocomplete fields — users must know the numeric IDs beforehand.  
**Impact:** Poor usability in the live application — users cannot easily create transfers without knowing database IDs.  
**Fix Applied:** None (architectural decision per decisions.md). The Playwright E2E tests use the API to fetch IDs first.  
**Status:** ⚠️ Accepted (Known Limitation)  
**Recommendation:** Add autocomplete/lookup controls for these fields in a future sprint.

---

## No New Regressions Found

The following previously fixed defects were verified as still working:
- D-08: FG batch dispatch works correctly (APPROVED batches only offered)
- D-37: PO list disables Approve for the submitter  
- D-39: FG hold approval uses Material dialog (no `prompt()`)
- D-40: No default user password in backend log

---

## Test Infrastructure Notes

1. **JavaScript component access pattern**: Angular DevTools API `window.ng.getComponent(el)` enables direct component method calls. This is essential for forms where Angular Material selects are hard to fill via DOM events.

2. **Signal vs property access**: Many Angular 18 components use signals — always test `typeof comp.field === 'function'` and call with `()` if true.

3. **Form reactive patching**: `comp.form.patchValue({...})` works reliably for all form controls, including nested FormArrays (use `linesArray.at(i).patchValue()`).

4. **Auth interceptor coverage**: Angular's `HttpClient` through the auth interceptor correctly adds Bearer tokens, so all API calls through components work authenticated. Direct `fetch()` calls return 401 without the token.
