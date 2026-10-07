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
