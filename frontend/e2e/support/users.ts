/** Role users from DESIGN §11.2 plus the finance/insight users the UAT checklist needs. */
export interface RoleUser {
  username: string;
  role: string;
  fullName: string;
}

export const ROLE_USERS: RoleUser[] = [
  { username: 'engineer1', role: 'ENGINEER', fullName: 'E2E Engineer' },
  { username: 'prodmgr1', role: 'PRODUCTION_MANAGER', fullName: 'E2E Production Manager' },
  { username: 'supervisor1', role: 'SUPERVISOR', fullName: 'E2E Supervisor' },
  { username: 'operator1', role: 'OPERATOR', fullName: 'E2E Operator One' },
  { username: 'operator2', role: 'OPERATOR', fullName: 'E2E Operator Two' },
  { username: 'quality1', role: 'QUALITY_MANAGER', fullName: 'E2E Quality Manager' },
  { username: 'storemgr1', role: 'STORE_MANAGER', fullName: 'E2E Store Manager' },
  { username: 'storeop1', role: 'STORE_OPERATOR', fullName: 'E2E Store Operator' },
  { username: 'purchase1', role: 'PURCHASE_MANAGER', fullName: 'E2E Purchase Manager' },
  { username: 'sales1', role: 'SALES', fullName: 'E2E Sales' },
  { username: 'dispatch1', role: 'DISPATCH', fullName: 'E2E Dispatch' },
  { username: 'maint1', role: 'MAINTENANCE', fullName: 'E2E Maintenance' },
  { username: 'mgmt1', role: 'MANAGEMENT', fullName: 'E2E Management' },
  { username: 'finance1', role: 'FINANCE_MANAGER', fullName: 'E2E Finance Manager' },
];

export const U = {
  admin: 'admin',
  engineer: 'engineer1',
  prodMgr: 'prodmgr1',
  supervisor: 'supervisor1',
  operator1: 'operator1',
  operator2: 'operator2',
  quality: 'quality1',
  storeMgr: 'storemgr1',
  storeOp: 'storeop1',
  purchase: 'purchase1',
  sales: 'sales1',
  dispatch: 'dispatch1',
  maint: 'maint1',
  mgmt: 'mgmt1',
  finance: 'finance1',
} as const;
