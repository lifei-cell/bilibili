import { defineConfig } from '@playwright/test'
import base from './playwright.config'

export default defineConfig({
  ...base,
  testIgnore: [],
  testMatch: '**/*.real.spec.ts',
  workers: 1,
  retries: 0,
  timeout: 240_000,
  expect: { timeout: 15_000 },
  use: {
    ...base.use,
    launchOptions: { args: ['--autoplay-policy=no-user-gesture-required'] },
  },
})
