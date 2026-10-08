/**
 * Segregation of duties for journals (business rule 19, SEC-008) and sales orders (RUNBOOK second approver,
 * ARCHITECTURE section 10). The Administrator holds every permission, yet cannot approve what they prepared or
 * submitted: the screen shows a disabled Approve with the reason, and the API refuses with SEGREGATION_OF_DUTIES.
 * A different user's approval then goes through. Payroll's initiator-cannot-approve rule is covered by HrmIT
 * (SOD-PAY-1) because a payroll run needs employees and attendance that the demo data does not provide.
 */
import { admin, apiFor } from '../support/api';
import { demoIds } from '../support/flow';
import { expect, openAs, test } from '../support/fixtures';
import { iconButton, row, today } from '../support/ui';
import { U } from '../support/users';

const SOD = /SEGREGATION_OF_DUTIES|different user|another approver/i;

async function accountId(code: string): Promise<number> {
  const accounts = await admin().list<any>('/api/gl/accounts');
  const found = accounts.find((a) => a.accountCode === code);
  if (!found) throw new Error(`GL account ${code} is missing from the seeded chart`);
  return found.id;
}

async function submittedJournalBy(api: ReturnType<typeof apiFor>, description: string): Promise<number> {
  const lines = [
    { glAccountId: await accountId('5201'), debitAmount: 750, creditAmount: 0, description: 'SoD debit' },
    { glAccountId: await accountId('1201'), debitAmount: 0, creditAmount: 750, description: 'SoD credit' },
  ];
  const j = await api.must('POST', '/api/journals', { journalType: 'GL', postingDate: today(), description, currencyCode: 'INR', lines });
  await api.must('POST', `/api/journals/${j.id}/submit`);
  return j.id;
}

test.describe('SoD-JRN: a journal cannot be approved by the user who prepared it', () => {
  test('SOD-JRN the preparer sees a disabled Approve and the API refuses; a different user approves', async ({ browser, guard }) => {
    guard.allow({ status: [400, 403, 409, 422], url: /\/api\/journals\/\d+\/approve$/ });
    const id = await submittedJournalBy(admin(), 'E2E SoD journal');

    const a = await openAs(browser, guard, U.admin, `/finance/journals/${id}`);
    const approve = a.page.getByRole('button', { name: 'Approve', exact: true });
    await expect(approve, 'Approve is disabled for the preparer').toBeDisabled();
    await approve.locator('xpath=ancestor::span[1]').hover();
    await expect(a.page.locator('.mat-mdc-tooltip')).toContainText(/another approver must approve/i);
    await a.close();

    const direct = await admin().call('POST', `/api/journals/${id}/approve`);
    expect(direct.ok, 'the API refuses the preparer too').toBe(false);
    expect(JSON.stringify(direct.body)).toMatch(SOD);
    expect((await admin().must('GET', `/api/journals/${id}`)).status, 'the journal stays SUBMITTED').toBe('SUBMITTED');

    // a different user holding the approval permission approves it
    const finance = apiFor(U.finance);
    const byFinance = await finance.call('POST', `/api/journals/${id}/approve`);
    expect(byFinance.ok, 'finance1 is a different user and may approve').toBe(true);
    expect((await admin().must('GET', `/api/journals/${id}`)).status).toBe('APPROVED');
  });
});

test.describe('SoD-SO: a sales order cannot be approved by the user who created or submitted it', () => {
  test('SOD-SO the submitter sees a disabled Approve and the API refuses; a different user approves', async ({ browser, guard }) => {
    guard.allow({ status: [400, 403, 409, 422], url: /\/api\/sales\/orders\/\d+\/approve$/ });
    const { product, customer } = await demoIds();
    const so = await admin().must('POST', '/api/sales/orders', {
      customerId: customer.id,
      orderDate: today(),
      requiredDate: today(10),
      deliveryAddress: 'E2E SoD address',
      items: [{ productId: product.id, orderedQty: 1, unitPrice: 100 }],
    });
    await admin().must('POST', `/api/sales/orders/${so.id}/confirm`);
    const full = await admin().must('GET', `/api/sales/orders/${so.id}`);

    const a = await openAs(browser, guard, U.admin, '/sales/orders');
    const approve = iconButton(row(a.page, full.orderNumber), 'thumb_up');
    await expect(approve, 'Approve is disabled for the user who created and submitted the order').toBeDisabled();
    await approve.locator('xpath=ancestor::span[1]').hover();
    await expect(a.page.locator('.mat-mdc-tooltip')).toContainText(/another approver must approve/i);
    await a.close();

    const direct = await admin().call('POST', `/api/sales/orders/${so.id}/approve`);
    expect(direct.ok, 'the API refuses the submitter').toBe(false);
    expect(JSON.stringify(direct.body)).toMatch(SOD);
    expect((await admin().must('GET', `/api/sales/orders/${so.id}`)).status, 'the order stays ORDERED').toBe('ORDERED');
  });

  test('SOD-SO-2 an order created and confirmed by sales1 is approved by the administrator', async () => {
    const { product, customer } = await demoIds();
    const sales = apiFor(U.sales);
    const so = await sales.must('POST', '/api/sales/orders', {
      customerId: customer.id,
      orderDate: today(),
      requiredDate: today(10),
      deliveryAddress: 'E2E SoD address 2',
      items: [{ productId: product.id, orderedQty: 1, unitPrice: 100 }],
    });
    await sales.must('POST', `/api/sales/orders/${so.id}/confirm`);
    const approved = await admin().must('POST', `/api/sales/orders/${so.id}/approve`);
    expect(approved.status).toBe('APPROVED');
    expect(approved.createdBy, 'the DTO names the creator').not.toBeNull();
    expect(approved.submittedBy, 'the DTO names the submitter').not.toBeNull();
  });
});
