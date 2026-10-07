/**
 * Finance screens built after the first walkthrough, driven through the real UI with ledger verification:
 * AP invoice entry with the three-way match (MATCHED, EXCEPTION, override by a different user, payment),
 * AR receipts, manual journal entry (live balance indicator, submit/approve/post/reverse) and a
 * post-flow check that every journal balances. Closing a fiscal period is deliberately NOT exercised here
 * (it would block postings for every later spec in the shared database); the backend ITs cover it.
 */
import type { Page } from '@playwright/test';
import { admin, apiFor, unique } from '../support/api';
import { expect, openAs, test } from '../support/fixtures';
import { demoIds, dispatchOnePiece, receiveViaApi } from '../support/flow';
import { dialog, fill, pick, row, today } from '../support/ui';
import { U } from '../support/users';

const money = (n: unknown): number => Math.round(Number(n) * 10_000) / 10_000;

async function invoiceableReceipt(supplierId: number, qty: number, rate = 285): Promise<{ id: number; grNumber: string; openValue: number }> {
  const rec = await receiveViaApi(qty, rate);
  const open = await admin().list<any>(`/api/lookups/goods-receipts/invoiceable?supplierId=${supplierId}`);
  const found = open.find((r) => r.id === rec.grId);
  expect(found, 'the new goods receipt is offered for invoicing').toBeTruthy();
  return found;
}

async function newInvoice(p: Page, supplierName: RegExp, grNumber: string, supplierInvoiceNo: string, subtotal: number): Promise<void> {
  await p.getByRole('button', { name: 'New invoice' }).click();
  const d = dialog(p);
  await pick(d, 'Supplier', supplierName);
  await pick(d, 'Goods receipt being billed', grNumber);
  await fill(d, "Supplier's invoice number", supplierInvoiceNo);
  await d.getByLabel('Invoice date').fill(today());
  await d.getByLabel('Due date').fill(today(30));
  await fill(d, 'Subtotal', subtotal);
  await d.getByRole('button', { name: 'Save invoice' }).click();
}

const invoiceBySupplierNo = async (no: string) => (await admin().list<any>('/api/ap/invoices?size=200')).find((i) => i.supplierInvoiceNo === no);

async function journalLines(id: number): Promise<{ code: string; dr: number; cr: number }[]> {
  const j = await admin().must('GET', `/api/journals/${id}`);
  return j.lines.map((l: any) => ({ code: l.accountCode, dr: money(l.debitAmount), cr: money(l.creditAmount) }));
}

test.describe.serial('AP invoice entry and the three-way match (UI)', () => {
  test('FIN-AP-1 finance1 enters a matching invoice: MATCHED, opened, journal balanced (Dr 2401 / Cr 2402)', async ({ browser, guard }) => {
    const { supplier } = await demoIds();
    const gr = await invoiceableReceipt(supplier.id, 10);
    const no = unique('SI-');
    const a = await openAs(browser, guard, U.finance, '/finance/ap');
    const p = a.page;
    await newInvoice(p, new RegExp(supplier.name.split(' ')[0], 'i'), gr.grNumber, no, gr.openValue);
    const d = dialog(p);
    await expect(d.getByText('It matches its goods receipt.')).toBeVisible();
    await d.getByRole('button', { name: 'Open invoice' }).click();
    await expect(d).toBeHidden();

    const inv = await invoiceBySupplierNo(no);
    expect(inv.status).toBe('OPEN');
    expect(inv.matchStatus).toBe('MATCHED');
    expect(money(inv.totalAmount)).toBe(money(gr.openValue));
    const lines = await journalLines(inv.journalHeaderId);
    expect(lines.reduce((n, l) => n + l.dr, 0)).toBeCloseTo(lines.reduce((n, l) => n + l.cr, 0), 4);
    expect(lines.find((l) => l.code === '2401')?.dr).toBeCloseTo(money(gr.openValue), 4);
    expect(lines.find((l) => l.code === '2402')?.cr).toBeCloseTo(money(gr.openValue), 4);

    // Part payment through the Pay dialog: amount limited to the balance.
    await p.reload();
    const r = row(p, inv.invoiceNumber);
    await r.getByRole('button', { name: 'Pay' }).click();
    const pay = dialog(p);
    await expect(pay.getByRole('heading', { name: 'Record payment' })).toBeVisible();
    await fill(pay, 'Amount', 1000);
    await pay.getByLabel('Payment date').fill(today());
    await pick(pay, 'Method', 0);
    await fill(pay, 'Bank or cheque reference', 'E2E-NEFT-1');
    await pay.getByRole('button', { name: 'Record payment' }).click();
    await expect(pay).toBeHidden();
    const after = await invoiceBySupplierNo(no);
    expect(after.status).toBe('PARTIALLY_PAID');
    expect(money(after.balanceAmount)).toBeCloseTo(money(gr.openValue) - 1000, 4);
    await a.close();
  });

  test('FIN-AP-2 an invoice above the receipt value is an EXCEPTION: it cannot be opened or paid, and the entering user cannot approve it', async ({ browser, guard }) => {
    guard.allow({ status: [400, 403, 409, 422], url: /\/api\/ap\/invoices/ });
    const { supplier } = await demoIds();
    const gr = await invoiceableReceipt(supplier.id, 10);
    const no = unique('SX-');
    const billed = money(gr.openValue + 650);
    const a = await openAs(browser, guard, U.finance, '/finance/ap');
    const p = a.page;
    await newInvoice(p, new RegExp(supplier.name.split(' ')[0], 'i'), gr.grNumber, no, billed);
    const d = dialog(p);
    await expect(d.getByText('It does not match its goods receipt.')).toBeVisible();
    await expect(d.getByRole('alert').first()).toContainText(/expected|receipt|exceed|invoice/i);
    await expect(d.getByRole('button', { name: 'Open invoice' }), 'an exception cannot be opened from the result dialog').toHaveCount(0);
    await d.getByRole('button', { name: 'Close' }).click();

    const inv = await invoiceBySupplierNo(no);
    expect(inv.status).toBe('DRAFT');
    expect(inv.matchStatus).toBe('EXCEPTION');
    const open = await apiFor(U.finance).post(`/api/ap/invoices/${inv.id}/open`).catch(() => null as any);
    const direct = open ?? (await apiFor(U.finance).call('PUT', `/api/ap/invoices/${inv.id}/open`));
    expect(direct.status, 'the API also refuses to open an exception').toBeGreaterThanOrEqual(400);

    // The user who entered it cannot approve the variance (segregation of duties).
    await p.reload();
    const r = row(p, inv.invoiceNumber);
    await expect(r.getByRole('button', { name: 'Open' })).toHaveCount(0);
    await expect(r.getByRole('button', { name: 'Pay' })).toHaveCount(0);
    await r.getByRole('button', { name: 'Approve variance' }).click();
    const panel = p.locator('section.override-panel');
    await panel.getByLabel('Reason for approving').fill('E2E self approval attempt');
    await panel.getByRole('button', { name: 'Approve variance' }).click();
    await expect.poll(async () => (await invoiceBySupplierNo(no)).matchStatus, { message: 'a self-override must not clear the exception' }).toBe('EXCEPTION');
    await a.close();

    // A different finance approver (admin holds AP_MATCH_OVERRIDE) approves with a reason, opens it; GR/IR clears exactly.
    const b = await openAs(browser, guard, U.admin, '/finance/ap');
    const q = b.page;
    const rr = row(q, inv.invoiceNumber);
    await rr.getByRole('button', { name: 'Approve variance' }).click();
    const panel2 = q.locator('section.override-panel');
    await panel2.getByLabel('Reason for approving').fill('E2E: supplier price confirmed in writing');
    await panel2.getByRole('button', { name: 'Approve variance' }).click();
    await expect.poll(async () => (await invoiceBySupplierNo(no)).matchStatus).toBe('OVERRIDDEN');
    await q.reload();
    await row(q, inv.invoiceNumber).getByRole('button', { name: 'Open' }).click();
    await expect.poll(async () => (await invoiceBySupplierNo(no)).status).toBe('OPEN');
    const done = await invoiceBySupplierNo(no);
    const lines = await journalLines(done.journalHeaderId);
    expect(lines.reduce((n, l) => n + l.dr, 0)).toBeCloseTo(lines.reduce((n, l) => n + l.cr, 0), 4);
    expect(lines.find((l) => l.code === '2401')?.dr).toBeCloseTo(money(gr.openValue), 4);
    expect(lines.find((l) => l.code === '5207')?.dr, 'the price variance goes to Purchase Price Variance').toBeCloseTo(650, 4);
    expect(lines.find((l) => l.code === '2402')?.cr).toBeCloseTo(billed, 4);
    await b.close();
  });
});

test.describe.serial('AR receipts (UI)', () => {
  test('FIN-AR-1 finance1 records a part receipt and then the rest; the invoice becomes PAID and over-balance is refused', async ({ browser, guard }) => {
    const { invoice: inv } = await dispatchOnePiece(200); // its own invoice: this test must not consume the shared demo invoices
    expect(inv.status).toBe('OPEN');
    const balance = money(inv.balanceAmount);
    const a = await openAs(browser, guard, U.finance, '/finance/ar');
    const p = a.page;
    const r = row(p, inv.invoiceNumber);
    await r.getByRole('button', { name: 'Record receipt' }).click();
    let d = dialog(p);
    await fill(d, 'Amount', balance + 1);
    await d.getByLabel('Receipt date').fill(today());
    await pick(d, 'Method', 0);
    await d.getByRole('button', { name: 'Record receipt' }).click();
    await expect(d, 'a receipt above the balance is refused and the dialog stays open').toBeVisible();
    await expect(d.getByText(/balance|exceed|more than|too much/i).first()).toBeVisible();
    expect(money((await admin().list<any>('/api/ar/invoices?size=200')).find((i) => i.id === inv.id)!.receivedAmount), 'nothing was recorded').toBe(0);
    await fill(d, 'Amount', 100);
    await d.getByRole('button', { name: 'Record receipt' }).click();
    await expect(d).toBeHidden();
    let after = (await admin().list<any>('/api/ar/invoices?size=200')).find((i) => i.id === inv.id);
    expect(after.status).toBe('PARTIALLY_PAID');
    expect(money(after.receivedAmount)).toBeCloseTo(100, 4);

    await row(p, inv.invoiceNumber).getByRole('button', { name: 'Record receipt' }).click();
    d = dialog(p);
    await fill(d, 'Amount', balance - 100);
    await d.getByLabel('Receipt date').fill(today());
    await pick(d, 'Method', 0);
    await d.getByRole('button', { name: 'Record receipt' }).click();
    await expect(d).toBeHidden();
    after = (await admin().list<any>('/api/ar/invoices?size=200')).find((i) => i.id === inv.id);
    expect(after.status).toBe('PAID');
    expect(money(after.balanceAmount)).toBe(0);
    await a.close();
  });
});

test.describe.serial('Manual journal entry (UI)', () => {
  let journalId = 0;

  test('FIN-JNL-1 the new-journal form shows a live balance and only saves a balanced journal', async ({ browser, guard }) => {
    const a = await openAs(browser, guard, U.finance, '/finance/journals/new');
    const p = a.page;
    await p.getByLabel('Description', { exact: true }).fill('E2E manual accrual');
    const pickAccount = async (n: number, code: string) => {
      await p.getByLabel(`Account ${n}`).fill(code);
      await p.getByRole('option').filter({ hasText: code }).first().click();
    };
    await pickAccount(1, '5201');
    await pickAccount(2, '1201');
    await p.getByLabel('Debit').nth(0).fill('1000');
    await expect(p.getByText(/Out of balance by/)).toBeVisible();
    await expect(p.getByRole('button', { name: 'Save draft' }), 'an unbalanced journal cannot be saved').toBeDisabled();
    await p.getByLabel('Credit').nth(1).fill('1000');
    await expect(p.getByText('Balanced', { exact: true })).toBeVisible();
    await p.getByRole('button', { name: 'Save draft' }).click();
    await expect(p).toHaveURL(/\/finance\/journals\/\d+/);
    journalId = Number(p.url().split('/').pop());
    await expect(p.getByText('DRAFT', { exact: false }).first()).toBeVisible();
    const j = await admin().must('GET', `/api/journals/${journalId}`);
    expect(j.status).toBe('DRAFT');
    expect(j.lines).toHaveLength(2);
    await a.close();
  });

  test('FIN-JNL-2 submit, approve (different user), post, then reverse with a reason: the original is REVERSED and the mirror balances', async ({ browser, guard }) => {
    expect(journalId, 'FIN-JNL-1 created the draft').toBeGreaterThan(0);
    const f = await openAs(browser, guard, U.finance, `/finance/journals/${journalId}`);
    await f.page.getByRole('button', { name: 'Submit for approval' }).click();
    await expect.poll(async () => (await admin().must('GET', `/api/journals/${journalId}`)).status).toBe('SUBMITTED');
    await f.close();

    const ap = await openAs(browser, guard, U.admin, `/finance/journals/${journalId}`);
    await ap.page.getByRole('button', { name: 'Approve' }).click();
    await expect.poll(async () => (await admin().must('GET', `/api/journals/${journalId}`)).status).toBe('APPROVED');
    await ap.close();

    const po = await openAs(browser, guard, U.finance, `/finance/journals/${journalId}`);
    await po.page.getByRole('button', { name: 'Post to ledger' }).click();
    await expect.poll(async () => (await admin().must('GET', `/api/journals/${journalId}`)).status).toBe('POSTED');
    await po.page.getByRole('button', { name: 'Reverse' }).click();
    const d = dialog(po.page);
    await d.getByRole('textbox').first().fill('E2E reversal: accrual entered in error');
    await d.getByRole('button').filter({ hasText: /reverse|confirm|yes/i }).last().click();
    await expect.poll(async () => (await admin().must('GET', `/api/journals/${journalId}`)).status, { message: 'D-previously-fixed: the original journal is marked REVERSED' }).toBe('REVERSED');
    const all = await admin().list<any>('/api/journals?size=200');
    const mirror = all.find((x) => x.reversalOfId === journalId);
    expect(mirror, 'a mirror journal exists').toBeTruthy();
    const lines = await journalLines(mirror.id);
    expect(lines.reduce((n, l) => n + l.dr, 0)).toBeCloseTo(1000, 4);
    expect(lines.reduce((n, l) => n + l.cr, 0)).toBeCloseTo(1000, 4);
    await po.close();
  });
});

test.describe('Post-flow finance integrity', () => {
  test('FIN-INT-1 every journal balances (Dr = Cr) and the ledger as a whole nets to zero', async () => {
    const journals = await admin().list<any>('/api/journals?size=500');
    expect(journals.length).toBeGreaterThan(5);
    let totalDr = 0;
    let totalCr = 0;
    for (const j of journals.filter((x) => x.status === 'POSTED' || x.status === 'REVERSED')) {
      const lines = await journalLines(j.id);
      const dr = lines.reduce((n, l) => n + l.dr, 0);
      const cr = lines.reduce((n, l) => n + l.cr, 0);
      expect(dr, `journal ${j.journalNumber} balances`).toBeCloseTo(cr, 4);
      totalDr += dr;
      totalCr += cr;
    }
    expect(totalDr).toBeCloseTo(totalCr, 3);
  });

  test('FIN-INT-2 each dispatch has an AR invoice and a COGS journal (Dr 5001 / Cr 1410) once costing exists', async () => {
    const dispatches = await admin().list<any>('/api/sales/dispatches?size=100');
    const invoices = await admin().list<any>('/api/ar/invoices?size=200');
    expect(dispatches.length).toBeGreaterThan(0);
    for (const dsp of dispatches) {
      expect(invoices.some((i) => i.dispatchId === dsp.id), `dispatch ${dsp.dispatchNumber ?? dsp.id} has an AR invoice`).toBe(true);
    }
    const journals = await admin().list<any>('/api/journals?size=500');
    let cogs = 0;
    for (const j of journals.filter((x) => /DISPATCH|SALES|COGS/i.test(`${x.sourceDocumentType} ${x.description}`))) {
      const lines = await journalLines(j.id);
      if (lines.some((l) => l.code === '5001' && l.dr > 0) && lines.some((l) => l.code === '1410' && l.cr > 0)) cogs++;
    }
    expect(cogs, 'at least one COGS journal (Dr 5001 / Cr 1410) exists').toBeGreaterThan(0);
  });
});
