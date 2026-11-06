import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'

// `npm run build` writes the UI into the Spring Boot jar's static folder, so the backend serves
// everything on http://localhost:8080. `npm run dev` runs Vite on :5173 and proxies the API.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }, // shadcn/ui + Watermelon UI imports
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1200, // single-page demo bundle (React, Recharts, Motion, Radix); code-splitting not needed
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/v3': 'http://localhost:8080',
      '/swagger-ui': 'http://localhost:8080',
    },
  },
})
