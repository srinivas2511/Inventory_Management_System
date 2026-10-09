import { admin } from '../support/api';
import { expect, openAs, test } from '../support/fixtures';
import { U } from '../support/users';

/**
 * GC-4 / SAL-005: the dispatch screen opens the printable PDF delivery note and packing list. The test clicks the real
 * buttons and checks, on the PDF response the browser received, the permission, the session token, a genuine PDF file
 * and a file name without path separators, and that the application opened a new tab for it. The text printed on the
 * pages is proven by DispatchDocumentRendererTest.
 */
test.describe('Dispatch documents (GC-4 / SAL-005)', () => {
  const documents = [
    { label: 'Delivery note', slug: 'delivery-note' },
    { label: 'Packing list', slug: 'packing-list' },
  ] as const;

  for (const { label, slug } of documents) {
    test(`GC-4 the ${label.toLowerCase()} button downloads a real PDF and opens it in a new tab`, async ({
      browser,
      guard,
    }) => {
      const dispatches = await admin().list<any>('/api/sales/dispatches?size=5&sort=id,desc');
      expect(dispatches.length, 'a dispatch exists (the demo load makes one)').toBeGreaterThan(0);
      const target = dispatches[0];

      const a = await openAs(browser, guard, U.dispatch, '/sales/dispatches');
      const row = a.page.getByRole('row').filter({ hasText: target.dispatchNumber }).first();
      await expect(row).toBeVisible();

      const [response, popup] = await Promise.all([
        a.page.waitForResponse((r) => r.url().includes(`/api/sales/dispatches/${target.id}/${slug}.pdf`)),
        a.page.waitForEvent('popup'),
        row.getByRole('button', { name: label }).click(),
      ]);

      expect(response.status()).toBe(200);
      expect(response.headers()['content-type']).toContain('application/pdf');
      const disposition = response.headers()['content-disposition'] ?? '';
      expect(disposition, 'the file name carries the dispatch number without path separators').toContain(
        `${String(target.dispatchNumber).replace(/[^A-Za-z0-9._-]/g, '-')}-${slug}.pdf`,
      );
      const body = await response.body();
      expect(body.subarray(0, 5).toString('latin1')).toBe('%PDF-');
      expect(body.length, 'a document with content, not an empty file').toBeGreaterThan(1000);

      await popup.close();
      await a.close();
    });
  }
});
