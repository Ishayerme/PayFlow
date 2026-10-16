import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In development the browser talks only to Vite (port 5173), and Vite forwards
// each API path to the service that owns it. Order matters: the most specific
// rule (statements live in transaction-service) must come before "/api".
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '^/api/accounts/\\d+/statement': 'http://localhost:8082',
      '/api/transfers': 'http://localhost:8082',
      '/api/notifications': 'http://localhost:8083',
      '/api': 'http://localhost:8081',
    },
  },
})
