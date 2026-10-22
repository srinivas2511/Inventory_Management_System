import { Api, admin, apiFor } from './api';
import { U } from './users';

const isoToday = (offset = 0) => new Date(Date.now() + offset * 86_400_000).toISOString().slice(0, 10);

export interface Balance {
  id: number;
  warehouseId: number;
  locationId: number;
  itemType: 'MATERIAL' | 'PRODUCT';
  materialId?: number;
  productId?: number;
  materialBatchId?: number;
  fgBatchId?: number;
  stockStatus: string;
  qtyOnHand: number;
  qtyReserved: number;
  qtyAvailable: number;
}

type Item = { materialId?: number; productId?: number };

export async function balances(api: Api = admin()): Promise<Balance[]> {
  return api.list<Balance>('/api/inventory/balances?size=1000');
}

function forItem(rows: Balance[], item: Item): Balance[] {
  return rows.filter((b) => (item.materialId ? b.materialId === item.materialId : b.productId === item.productId));
}

/** Sum of qtyOnHand for an item in one stock status ('*' = all statuses). */
export async function onHand(item: Item, status: string, api: Api = admin()): Promise<number> {
  const rows = forItem(await balances(api), item);
  return rows.filter((b) => status === '*' || b.stockStatus === status).reduce((n, b) => n + Number(b.qtyOnHand), 0);
}

export async function availableOf(item: Item, api: Api = admin()): Promise<number> {
  const rows = forItem(await balances(api), item);
  return rows.filter((b) => b.stockStatus === 'AVAILABLE').reduce((n, b) => n + Number(b.qtyAvailable), 0);
}

export async function demoIds(api: Api = admin()) {
  const material = (await api.list('/api/materials?size=100')).find((m: any) => m.materialCode === 'DEMO-WR-65SI2');
  const product = (await api.list('/api/products?size=100')).find((p: any) => /25X3/i.test(p.productCode ?? p.code ?? p.name ?? ''));
  const supplier = (await api.list('/api/suppliers?size=100'))[0];
  const customer = (await api.list('/api/customers?size=100'))[0];
  const warehouse = (await api.list('/api/warehouses'))[0];
  if (!material || !product || !supplier || !customer || !warehouse) {
    throw new Error(
      'Demo master data missing: ' +
        JSON.stringify({ material: !!material, product: !!product, supplier: !!supplier, customer: !!customer, warehouse: !!warehouse }),
    );
  }
  return { material, product, supplier, customer, warehouse };
}

export const statusRe = (s: string): RegExp => new RegExp(s.replace(/_/g, '[ _]'), 'i');

export interface Received {
  poId: number;
  poNumber: string;
  grId: number;
  poItemId: number;
  batchId: number;
  inspectionId: number;
  qty: number;
}

/** PO -> approve (different user) -> send -> goods receipt through the real API; the batch lands in QUARANTINE. */
export async function receiveViaApi(qty: number, rate = 285): Promise<Received> {
  const { supplier, material, warehouse } = await demoIds();
  const purchase = apiFor(U.purchase);
  const po = await purchase.must('POST', '/api/procurement/purchase-orders', {
    supplierId: supplier.id,
    poDate: isoToday(),
    items: [{ materialId: material.id, orderedQty: qty, rate, taxPct: 0 }],
  });
  await purchase.must('POST', `/api/procurement/purchase-orders/${po.id}/submit`);
  await admin().must('POST', `/api/procurement/purchase-orders/${po.id}/approve`);
  await admin().must('POST', `/api/procurement/purchase-orders/${po.id}/send-to-supplier`);
  const full = await admin().must('GET', `/api/procurement/purchase-orders/${po.id}`);
  const stock = (await balances()).find((b) => b.materialId === material.id && b.stockStatus === 'AVAILABLE');
  const gr = await apiFor(U.storeMgr).must('POST', '/api/procurement/goods-receipts', {
    purchaseOrderId: po.id,
    receiptDate: isoToday(),
    warehouseId: warehouse.id,
    items: [{ poItemId: full.items[0].id, receivedQty: qty, locationId: stock!.locationId, heatNumber: 'H-E2E', certificateNumber: 'MTC-E2E' }],
  });
  const batch = (await admin().list<any>('/api/inventory/batches?size=500')).filter((b) => b.status === 'QUARANTINE').sort((x, y) => y.id - x.id)[0];
  const insp = (await admin().list<any>('/api/quality/inspections?size=500'))
    .filter((i) => i.inspectionType === 'INCOMING' && /PENDING|IN_PROGRESS/.test(i.status))
    .sort((x, y) => y.id - x.id)[0];
  return { poId: po.id, poNumber: full.poNumber, grId: gr.id, poItemId: full.items[0].id, batchId: batch.id, inspectionId: insp.id, qty };
}

/** A RELEASED production order for the demo product through the real API (prodmgr1). */
export async function releasedOrder(plannedQty: number) {
  const { product } = await demoIds();
  const bom = await admin().must('GET', `/api/products/${product.id}/bom`);
  const prod = apiFor(U.prodMgr);
  const order = await prod.must('POST', '/api/production-orders', { productId: product.id, bomId: bom.id, plannedQty, priority: 3 });
  await prod.must('POST', `/api/production-orders/${order.id}/approve`);
  await prod.must('POST', `/api/production-orders/${order.id}/release`);
  const ops = await admin().list<any>(`/api/production-orders/${order.id}/operations`);
  return { id: order.id as number, orderNumber: order.orderNumber as string, ops };
}

/**
 * A one-piece sales order -> reservation -> dispatch through the real API, so a spec that needs an OPEN AR invoice
 * creates its own instead of consuming the shared demo invoices. Needs AVAILABLE finished goods (the workflow spec or
 * the demo data provides them) and says so clearly when there are none.
 */
export async function dispatchOnePiece(unitPrice = 200): Promise<{ dispatchId: number; invoice: any }> {
  const { product, customer } = await demoIds();
  const fgList = await admin().list<any>(`/api/lookups/fg-stock?productId=${product.id}`);
  const fg = fgList.find((b) => Number(b.available) >= 1);
  if (!fg) throw new Error('dispatchOnePiece needs AVAILABLE APPROVED finished goods: run demo-workflow.spec.ts first (or load the demo data)');
  const sales = apiFor(U.sales);
  const so = await sales.must('POST', '/api/sales/orders', {
    customerId: customer.id,
    orderDate: isoToday(),
    requiredDate: isoToday(10),
    deliveryAddress: 'E2E Plot 1, Test Industrial Area',
    items: [{ productId: product.id, orderedQty: 1, unitPrice }],
  });
  await sales.must('POST', `/api/sales/orders/${so.id}/confirm`);
  await admin().must('POST', `/api/sales/orders/${so.id}/approve`);
  const full = await admin().must('GET', `/api/sales/orders/${so.id}`);
  await sales.must('POST', '/api/sales/reservations', {
    salesOrderItemId: full.items[0].id,
    fgBatchId: fg.fgBatchId,
    warehouseId: fg.warehouseId,
    locationId: fg.locationId,
    qty: 1,
  });
  const dispatch = await apiFor(U.dispatch).must('POST', '/api/sales/dispatches', {
    salesOrderId: so.id,
    dispatchDate: isoToday(),
    deliveryAddress: 'E2E Plot 1, Test Industrial Area',
    lines: [{ salesOrderItemId: full.items[0].id, fgBatchId: fg.fgBatchId, quantity: 1, warehouseId: fg.warehouseId, locationId: fg.locationId, unitPrice }],
  });
  let invoice: any;
  for (let i = 0; i < 30 && !invoice; i++) {
    invoice = (await admin().list<any>('/api/ar/invoices?size=200')).find((x) => x.dispatchId === dispatch.id);
    if (!invoice) await new Promise((r) => setTimeout(r, 500));
  }
  if (!invoice) throw new Error(`no AR invoice appeared for dispatch ${dispatch.id}`);
  return { dispatchId: dispatch.id, invoice };
}

export interface Produced {
  orderId: number;
  orderNumber: string;
  fgBatchId: number;
  fgBatchNumber: string;
  inspectionId: number;
  pieces: number;
  issue: any;
}

/**
 * A whole production run through the real API: wire received and accepted, a released order, the planned material
 * issued, every operation started / output recorded / completed in sequence. Leaves one FG batch in QUALITY_PENDING with its
 * FINAL inspection waiting (the after-commit path), so a spec can take it from there through the UI.
 */
export async function produceViaApi(pieces: number): Promise<Produced> {
  const { warehouse } = await demoIds();
  const order = await releasedOrder(pieces);
  let mats: any[] = [];
  for (let i = 0; i < 30 && mats.length === 0; i++) {
    mats = await admin().list<any>(`/api/production-orders/${order.id}/materials`);
    if (mats.length === 0) await new Promise((r) => setTimeout(r, 500));
  }
  if (mats.length === 0) throw new Error(`order ${order.orderNumber} has no material snapshot`);
  const planned = mats.reduce((n, m) => n + Number(m.plannedQty), 0);
  const rec = await receiveViaApi(Math.max(1, Math.ceil(planned)));
  await apiFor(U.quality).must('POST', `/api/quality/inspections/${rec.inspectionId}/accept`, { acceptedQty: rec.qty, rejectedQty: 0 });
  const issue = await admin().must('POST', '/api/production/issues', {
    productionOrderId: order.id,
    warehouseId: warehouse.id,
    lines: mats.map((m) => ({ materialId: m.materialId, quantity: Number(m.plannedQty) })),
  });
  const ops = [...order.ops].sort((a: any, b: any) => a.sequenceNo - b.sequenceNo);
  for (const op of ops) {
    await admin().must('POST', `/api/production/operations/${op.id}/start`);
    await admin().must('POST', '/api/production/outputs', { productionOperationId: op.id, goodQty: pieces, rejectedQty: 0, scrapQty: 0 });
    await admin().must('POST', `/api/production/operations/${op.id}/complete`);
  }
  let fg: any;
  let insp: any;
  for (let i = 0; i < 40 && !(fg && insp); i++) {
    fg = (await admin().list<any>('/api/quality/fg-batches?size=50')).sort((x, y) => y.id - x.id)[0];
    if (fg) insp = (await admin().list<any>('/api/quality/inspections?size=200')).find((q) => q.inspectionType === 'FINAL' && q.fgBatchId === fg.id);
    if (!(fg && insp)) await new Promise((r) => setTimeout(r, 500));
  }
  if (!fg || !insp) throw new Error(`no FG batch / final inspection appeared for order ${order.orderNumber}`);
  return { orderId: order.id, orderNumber: order.orderNumber, fgBatchId: fg.id, fgBatchNumber: fg.batchNumber, inspectionId: insp.id, pieces, issue };
}

/** A confirmed and approved sales order for `qty` pieces of the demo product. */
export async function approvedSalesOrder(qty: number, unitPrice = 200): Promise<{ id: number; orderNumber: string; itemId: number }> {
  const { product, customer } = await demoIds();
  const sales = apiFor(U.sales);
  const so = await sales.must('POST', '/api/sales/orders', {
    customerId: customer.id,
    orderDate: isoToday(),
    requiredDate: isoToday(10),
    deliveryAddress: 'E2E Plot 1, Test Industrial Area',
    items: [{ productId: product.id, orderedQty: qty, unitPrice }],
  });
  await sales.must('POST', `/api/sales/orders/${so.id}/confirm`);
  await admin().must('POST', `/api/sales/orders/${so.id}/approve`);
  const full = await admin().must('GET', `/api/sales/orders/${so.id}`);
  return { id: so.id, orderNumber: full.orderNumber, itemId: full.items[0].id };
}
