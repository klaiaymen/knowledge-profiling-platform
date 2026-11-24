import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  reporter: 'list',
  use: { ...devices['Desktop Chrome'], baseURL: process.env.KPF_FRONTEND_URL ?? 'http://127.0.0.1:4200', trace: 'retain-on-failure' },
  webServer: {
    command: 'npm run start -- --host 127.0.0.1',
    url: process.env.KPF_FRONTEND_URL ?? 'http://127.0.0.1:4200',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  }
});
