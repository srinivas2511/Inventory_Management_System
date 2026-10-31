import { defineConfig, devices } from '@playwright/test';

/**
 * E2E release-validation suite. Run against a live stack:
 *   E2E_BASE_URL  - frontend (default http://localhost:4200)
 *   E2E_API_URL   - backend  (default http://localhost:8090)
 * The backend must run with the dev/demo profile and IMS_DEMO_LOAD_DATA=true.
 * Specs share one database and run serially (workers: 1) because the workflow
 * spec hands state from step to step.
 */
export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  fullyParallel: false,
  workers: 1,
  retries: 1,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env['E2E_BASE_URL'] || 'http://localhost:4200',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
