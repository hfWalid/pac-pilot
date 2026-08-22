import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    // fake-indexeddb gives the store tests a real IndexedDB implementation rather than a mock, so
    // what they exercise is the same API the browser runs — transactions, indexes and all.
    setupFiles: ['./src/test-setup.ts'],
  },
});
