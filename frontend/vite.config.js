import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// `npm run build` writes the UI into the Spring Boot jar's static folder, so the backend serves
// everything on http://localhost:8080. `npm run dev` runs Vite on :5173 and proxies the API.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
    chunkSizeWarningLimit: 900, // single-page demo bundle (React + Recharts); code-splitting not needed
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
