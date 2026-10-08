/**
 * R9-7 finance features through the real UI: GST on a supplier invoice (FIN-007), a foreign-currency invoice with its
 * realised exchange loss (FIN-009), an AR credit note that reverses tax in proportion (FIN-004), step-up
 * authentication on a payment (SEC-010) and module activation (REQ §1). Hand-worked numbers: GST 18% on 20,000 is
 * 3,600; USD 250 at 80 is INR 20,000 and settling at 82 is a loss of 500; crediting 10,000 of a 20,000 invoice taxed at
 * 18% reverses 1,800 of its 3,600. Every setting a test changes is restored in a finally block.
 */
import { admin, apiFor, passwordFor, unique } from '../support/api';
import { expect, openAs, test } from '../support/fixtures';
import { demoIds, dispatchOnePiece, receiveViaApi } from '../support/flow';
import { dialog, expectToast, fill, pick, row, today } from '../support/ui';
import { U } from '../support/users';

const money = (n: unknown): number => Math.round(Number(n) * 10_000) / 10_000;

/** A goods receipt of `qty` x `rate` against a purchase order taxed at `taxPct` (receiveViaApi uses 0%). */
async function receiveTaxed(qty: number, rate: number, taxPct: number): Promise<{ grId: number }> {
  const { supplier, material, warehouse } = await demoIds();
  const purchase = apiFor(U.purchase);
  const po = await purchase.must('POST', '/api/procurement/purchase-orders', {
    supplierId: supplier.id,
    poDate: today(),
    items: [{ materialId: material.id, orderedQty: qty, rate, taxPct }],
  });
  await purchase.must('POST', `/api/procurement/purchase-orders/${po.id}/submit`);
  await admin().must('POST', `/api/procurement/purchase-orders/${po.id}/approve`);
  await admin().must('POST', `/api/procurement/purchase-orders/${po.id}/send-to-supplier`);
  const full = await admin().must('GET', `/api/procurement/purchase-orders/${po.id}`);
  const stock = (await admin().list<any>('/api/inventory/balances?size=500')).find(
    (b) => b.materialId === material.id && b.stockStatus === 'AVAILABLE',
  );
  const gr = await apiFor(U.storeMgr).must('POST', '/api/procurement/goods-receipts', {
    purchaseOrderId: po.id,
    receiptDate: today(),
    warehouseId: warehouse.id,
    items: [
      {
        poItemId: full.items[0].id,
        receivedQty: qty,
        locationId: stock!.locationId,
        heatNumber: 'H-R97',
        certificateNumber: 'MTC-R97',
      },
    ],
  });
  return { grId: gr.id };
}

async function journalLines(id: number): Promise<{ code: string; dr: number; cr: number }[]> {
  const j = await admin().must('GET', `/api/journals/${id}`);
  return j.lines.map((l: any) => ({ code: l.accountCode, dr: money(l.debitAmount), cr: money(l.creditAmount) }));
}

const sum = (lines: { dr: number; cr: number }[], side: 'dr' | 'cr') => money(lines.reduce((n, l) => n + l[side], 0));

test.describe.serial('R9-7 finance features (UI)', () => {
  test('FIN-R97-1 GST 18% on a 20,000 supplier invoice: previewed on the form, split by place of supply, posted as input tax', async ({
    browser,
    guard,
  }) => {
    const { supplier } = await demoIds();
    const received = await receiveTaxed(100, 200, 18);
    const open = await admin().list<any>(`/api/lookups/goods-receipts/invoiceable?supplierId=${supplier.id}`);
    const gr = open.find((r) => r.id === received.grId);
    expect(gr, 'the new goods receipt is offered for invoicing').toBeTruthy();
    const no = unique('GST-');
    const a = await openAs(browser, guard, U.finance, '/finance/ap');
    const p = a.page;
    await p.getByRole('button', { name: 'New invoice' }).click();
    const d = dialog(p);
    await pick(d, 'Supplier', new RegExp(supplier.name.split(' ')[0], 'i'));
    await pick(d, 'Goods receipt being billed', gr.grNumber);
    await fill(d, "Supplier's invoice number", no);
    await d.getByLabel('Invoice date').fill(today());
    await d.getByLabel('Due date').fill(today(30));
    await pick(d, 'GST slab', /GST18/);
    await fill(d, 'Subtotal', 20000);

    const preview = d.getByTestId('tax-preview');
    await expect(preview).toContainText('Tax 3,600.00');
    const expected = await admin().must(
      'GET',
      `/api/finance/tax/determine?direction=PURCHASE&slab=GST18&partnerId=${supplier.id}&amount=20000`,
    );
    expect(money(expected.taxTotal)).toBe(3600);
    for (const l of expected.lines) {
      await expect(preview).toContainText(
        `${l.taxType} ${l.ratePct}% on 20,000.00 = ${Number(l.taxAmount).toLocaleString('en-US', { minimumFractionDigits: 2 })}`,
      );
    }
    await d.getByRole('button', { name: 'Save invoice' }).click();
    await expect(d.getByText('It matches its goods receipt.')).toBeVisible();
    await d.getByRole('button', { name: 'Open invoice' }).click();
    await expect(d).toBeHidden();

    const inv = (await admin().list<any>('/api/ap/invoices?size=200')).find((i) => i.supplierInvoiceNo === no);
    expect(inv.status).toBe('OPEN');
    expect(money(inv.taxTotal)).toBe(3600);
    expect(money(inv.totalAmount)).toBe(23600);
    expect(inv.taxLines.map((l: any) => l.taxType).sort()).toEqual(expected.lines.map((l: any) => l.taxType).sort());
    const lines = await journalLines(inv.journalHeaderId);
    expect(sum(lines, 'dr')).toBe(sum(lines, 'cr'));
    expect(lines.find((l) => l.code === '2401')?.dr).toBe(20000);
    expect(lines.find((l) => l.code === '2402')?.cr).toBe(23600);
    expect(money(lines.filter((l) => /^131/.test(l.code)).reduce((n, l) => n + l.dr, 0))).toBe(3600);
    await a.close();
  });

  test('FIN-R97-2 USD 250 at 80 is INR 20,000; paid at 82 it is a realised loss of 500, shown in the payments of the invoice', async ({
    browser,
    guard,
  }) => {
    const periods: any[] = await admin().must('GET', '/api/periods');
    const now = periods.find((x) => x.startDate <= today() && today() <= x.endDate);
    const next = periods
      .filter((x) => x.startDate > now.endDate)
      .sort((x, y) => x.startDate.localeCompare(y.startDate))[0];
    expect(next, 'a following fiscal period exists to pay in').toBeTruthy();

    // maintain the two rates through the screen
    const fin = await openAs(browser, guard, U.admin, '/finance/exchange-rates');
    const rp = fin.page;
    for (const [period, rate] of [
      [now, 80],
      [next, 82],
    ] as const) {
      await fill(rp.locator('form'), 'Currency', 'USD');
      await pick(rp.locator('form'), 'Period', period.periodName);
      await fill(rp.locator('form'), 'Rate in INR', rate);
      await rp.getByRole('button', { name: 'Save rate' }).click();
      await expectToast(rp, `USD saved for ${period.periodName}`);
    }
    await expect(row(rp, now.periodName)).toContainText('80.00');
    await expect(row(rp, next.periodName)).toContainText('82.00');
    await fin.close();

    const { supplier } = await demoIds();
    const rec = await receiveViaApi(100, 200); // 20,000 INR received
    const open = await admin().list<any>(`/api/lookups/goods-receipts/invoiceable?supplierId=${supplier.id}`);
    const gr = open.find((r) => r.id === rec.grId);
    const no = unique('USD-');
    const a = await openAs(browser, guard, U.finance, '/finance/ap');
    const p = a.page;
    await p.getByRole('button', { name: 'New invoice' }).click();
    const d = dialog(p);
    await pick(d, 'Supplier', new RegExp(supplier.name.split(' ')[0], 'i'));
    await pick(d, 'Goods receipt being billed', gr.grNumber);
    await fill(d, "Supplier's invoice number", no);
    await d.getByLabel('Invoice date').fill(today());
    await d.getByLabel('Due date').fill(today(30));
    await fill(d, 'Currency', 'usd');
    await fill(d, 'Subtotal', 250);
    await d.getByRole('button', { name: 'Save invoice' }).click();
    await expect(d.getByText('It matches its goods receipt.')).toBeVisible();
    await expect(d.getByTestId('result-inr')).toContainText('20,000.00');
    await d.getByRole('button', { name: 'Open invoice' }).click();
    await expect(d).toBeHidden();

    const inv = (await admin().list<any>('/api/ap/invoices?size=200')).find((i) => i.supplierInvoiceNo === no);
    expect(inv.currencyCode).toBe('USD');
    expect(money(inv.exchangeRate)).toBe(80);
    expect(money(inv.totalInr)).toBe(20000);

    await p.reload();
    const r = row(p, inv.invoiceNumber);
    await expect(r).toContainText('= 20,000.00 INR at 80.00');
    await r.getByRole('button', { name: 'Pay' }).click();
    const pay = dialog(p);
    await fill(pay, 'Amount', 250);
    await pay.getByLabel('Payment date').fill(next.startDate);
    await pay.getByRole('button', { name: 'Record payment' }).click();
    await expect(pay).toBeHidden();

    await row(p, inv.invoiceNumber).getByRole('button', { name: 'History' }).click();
    const history = dialog(p);
    await expect(history.getByTestId('fx-difference')).toContainText('Loss 500.00');
    await expect(history).toContainText('20,000.00');
    await history.getByRole('button', { name: 'Close' }).click();

    const payments = await admin().must('GET', `/api/ap/invoices/${inv.id}/payments`);
    expect(payments).toHaveLength(1);
    expect(money(payments[0].fxDifference)).toBe(-500);
    expect(money(payments[0].carryingInr)).toBe(20000);
    const lines = await journalLines(payments[0].journalHeaderId);
    expect(sum(lines, 'dr')).toBe(sum(lines, 'cr'));
    expect(lines.find((l) => l.code === '5208')?.dr).toBe(500);
    await a.close();
  });

  test('FIN-R97-3 a credit note of 10,000 on a 20,000 invoice taxed at 18% reverses 1,800 of its 3,600; another user approves it', async ({
    browser,
    guard,
  }) => {
    const { product } = await demoIds();
    const setup = await openAs(browser, guard, U.admin, '/finance/tax');
    const sp = setup.page;
    const assign = sp.locator('form').filter({ has: sp.getByRole('button', { name: 'Save slab' }) });
    await pick(assign, 'Product', /25X3/i);
    await pick(assign, 'Slab', /GST18/);
    await assign.getByRole('button', { name: 'Save slab' }).click();
    await expectToast(sp, 'Slab GST18 set');
    await setup.close();

    try {
      const { invoice } = await dispatchOnePiece(20000);
      expect(money(invoice.taxTotal)).toBe(3600);

      const a = await openAs(browser, guard, U.finance, '/finance/ar');
      const p = a.page;
      await row(p, invoice.invoiceNumber).getByRole('button', { name: 'Credit note' }).click();
      const d = dialog(p);
      await fill(d, 'Amount to credit, before tax', 10000);
      await fill(d, 'Reason', 'Damaged in transit');
      await expect(d.getByTestId('tax-reversal')).toContainText('Tax reversed 1,800.00');
      await expect(d.getByTestId('tax-reversal')).toContainText('credit total 11,800.00');
      await d.getByRole('button', { name: 'Create credit note' }).click();
      await expectToast(p, /created: it needs approval by another user/);

      await p.goto('/finance/credit-notes');
      const mine = row(p, invoice.invoiceNumber);
      await expect(mine).toContainText('DRAFT');
      await expect(mine.getByRole('button', { name: 'Approve' })).toBeDisabled();
      await a.close();

      const b = await openAs(browser, guard, U.admin, '/finance/credit-notes');
      await row(b.page, invoice.invoiceNumber).getByRole('button', { name: 'Approve' }).click();
      await expectToast(b.page, /approved and posted/);
      await b.close();

      const note = (await admin().list<any>('/api/ar/credit-notes?size=200')).find(
        (n) => n.invoiceNumber === invoice.invoiceNumber,
      );
      expect(note.status).toBe('POSTED');
      expect(money(note.subtotal)).toBe(10000);
      expect(money(note.taxTotal)).toBe(1800);
      expect(money(note.totalAmount)).toBe(11800);
      expect(money(note.taxLines.reduce((n: number, l: any) => n + Number(l.taxAmount), 0))).toBe(1800);
      const lines = await journalLines(note.journalHeaderId);
      expect(sum(lines, 'dr')).toBe(sum(lines, 'cr'));
      expect(sum(lines, 'cr')).toBe(11800);
    } finally {
      await admin().must('PUT', `/api/finance/tax/product-slabs/${product.id}`, { slabCode: null });
    }
  });

  test('FIN-R97-4 with step-up on, paying a supplier asks for the password once; the payment then goes through', async ({
    browser,
    guard,
  }) => {
    guard.allow({ status: 403, url: /\/api\/ap\/invoices\/\d+\/payments$/ });
    const { supplier } = await demoIds();
    const rec = await receiveViaApi(10, 285);
    const open = await admin().list<any>(`/api/lookups/goods-receipts/invoiceable?supplierId=${supplier.id}`);
    const gr = open.find((r) => r.id === rec.grId);
    const no = unique('SU-');
    const created = await apiFor(U.finance).must('POST', '/api/ap/invoices', {
      supplierId: supplier.id,
      supplierInvoiceNo: no,
      invoiceDate: today(),
      dueDate: today(30),
      goodsReceiptId: gr.id,
      subtotal: money(gr.openValue),
      taxTotal: 0,
    });
    await apiFor(U.finance).must('PUT', `/api/ap/invoices/${created.id}/open`);

    await admin().must('PUT', '/api/admin/settings/security.stepup.enabled', { value: 'true' });
    try {
      const a = await openAs(browser, guard, U.finance, '/finance/ap');
      const p = a.page;
      await row(p, created.invoiceNumber).getByRole('button', { name: 'Pay' }).click();
      const pay = dialog(p);
      await fill(pay, 'Amount', 100);
      await pay.getByRole('button', { name: 'Record payment' }).click();
      const prompt = p.getByRole('dialog').filter({ hasText: 'Confirm it is you' });
      await expect(prompt).toBeVisible();
      await prompt.getByLabel('Password').fill(passwordFor(U.finance));
      await prompt.getByTestId('step-up-confirm').click();
      await expect(prompt).toBeHidden();
      await expect(pay).toBeHidden();

      const after = (await admin().list<any>('/api/ap/invoices?size=200')).find((i) => i.id === created.id);
      expect(after.status).toBe('PARTIALLY_PAID');
      expect(money(after.paidAmount)).toBe(100);
      await a.close();
    } finally {
      await admin().must('PUT', '/api/admin/settings/security.stepup.enabled', { value: 'false' });
    }
  });

  test('FIN-R97-5 switching the CRM module off hides its screens and refuses its API; switching it on restores both', async ({
    browser,
    guard,
  }) => {
    const a = await openAs(browser, guard, U.admin, '/admin/modules');
    const p = a.page;
    const crmLinks = p.locator('a[href^="/crm"]');
    try {
      await expect(crmLinks.first()).toBeAttached();
      await p.getByTestId('module-CRM').getByRole('switch').click();
      await expectToast(p, /CRM|Customer relationship/i);
      await p.reload();
      await expect(p.locator('a[href^="/crm"]')).toHaveCount(0);
      const refused = await admin().call('GET', '/api/crm/leads');
      expect(refused.status).toBe(403);
      expect(refused.body.code).toBe('MODULE_INACTIVE');
    } finally {
      await admin().must('PUT', '/api/modules/CRM', { active: true });
    }
    await p.reload();
    await expect(p.locator('a[href^="/crm"]').first()).toBeAttached();
    const restored = await admin().call('GET', '/api/crm/leads');
    expect(restored.status).toBe(200);
    await a.close();
  });
});
