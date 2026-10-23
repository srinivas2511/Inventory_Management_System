# e2e/support/ CLAUDE.md

Shared helpers used by every spec file. Import from here — never duplicate logic in individual specs.

## Files
| File | What it provides |
|------|-----------------|
| `env.ts` | Environment variable constants: `BASE`, `API`, `ADMIN_USER`, `ADMIN_PASSWORD`, `ROLE_PASSWORD`, `TEMP_PASSWORD` |
| `users.ts` | `ROLE_USERS` array — the 14 DESIGN §11.2 role users with username, role, and full name |
| `api.ts` | `Api` class (wraps Playwright `APIRequestContext`); `admin()`, `apiFor(user)`, `login()`, `rawApi()` factory functions |
| `flow.ts` | Typed multi-step business flows: `dispatchOnePiece()`, `createSalesOrder()`, `receiveGoods()`, etc. Also: `balances()`, `onHand()`, `availableOf()` stock helpers |
| `ui.ts` | UI interaction helpers: `typeInto()` (dialog-safe typing), `waitForDialogFocus()`, `dialog()` locator shorthand |
| `fixtures.ts` | Playwright `test` extended with fixtures: per-test logged-in `page` for each role, `adminPage`, `expect` with custom matchers |

## Critical: `typeInto()` instead of `fill()`
Angular Material moves focus into a dialog one render after the open animation, so a bare `locator.fill()` can race and send text to the wrong field. **Always use `typeInto(locator, value)` for any field inside a dialog.** It waits for `waitForDialogFocus()` to confirm the dialog is ready before typing.

## `Api` class (api.ts)
```typescript
const api = admin();                          // API client authenticated as admin
const api = apiFor('sales1');                 // API client for a role user
await api.get('/api/inventory/balances');
await api.post('/api/sales/orders', body);
await api.list<T>('/api/sales/orders?size=100');  // GET + unwrap .content[]
```

## Stock helpers (flow.ts)
```typescript
const qty = await onHand({ materialId: 1 }, 'AVAILABLE');   // qty in AVAILABLE
const qty = await availableOf({ productId: 2 });             // qtyAvailable summed
const all = await balances();                                  // all balance rows
```

## Role users (users.ts)
Usernames: `engineer1`, `prodmgr1`, `supervisor1`, `operator1`, `operator2`, `quality1`, `storemgr1`, `storeop1`, `purchase1`, `sales1`, `dispatch1`, `maint1`, `finance1`, `admin`.
All role users share `ROLE_PASSWORD`; `admin` uses `ADMIN_PASSWORD`.
