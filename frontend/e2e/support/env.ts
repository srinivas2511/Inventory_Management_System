export const BASE = process.env['E2E_BASE_URL'] || 'http://localhost:4200';
export const API = process.env['E2E_API_URL'] || 'http://localhost:8090';

/** Documented demo administrator (README, RUNBOOK, UAT checklist). */
export const ADMIN_USER = process.env['E2E_ADMIN_USER'] || 'admin';
export const ADMIN_PASSWORD = process.env['E2E_ADMIN_PASSWORD'] || 'Admin@123';

/** DESIGN §11.2 initial password; users are created with it, then changed to ROLE_PASSWORD. */
export const TEMP_PASSWORD = 'Demo@12345!';
export const ROLE_PASSWORD = process.env['E2E_ROLE_PASSWORD'] || 'Qa#Plant-2026x!';
