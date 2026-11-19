import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  build: {
    // Straight into the jar's static folder: Spring Boot serves the page, so there is one deploy and no CORS.
    outDir: '../target/classes/static',
    emptyOutDir: true,
  },
  server: {
    // `npm run dev` talks to the Spring app running on :8080.
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
