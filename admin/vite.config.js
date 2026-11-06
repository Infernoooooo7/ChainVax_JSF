import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The admin console is served by the Spring Boot backend at /admin (hash routing: /admin/#/users).
// The build lands in backend/src/main/resources/admin-ui/admin, separate from the staff UI's static/ folder,
// so rebuilding one UI never wipes the other. Dev mode: npm run dev -> http://localhost:5174/admin/
export default defineConfig({
  base: '/admin/',
  plugins: [react()],
  build: {
    outDir: '../backend/src/main/resources/admin-ui/admin',
    emptyOutDir: true,
    chunkSizeWarningLimit: 3000,
  },
  server: {
    port: 5174,
    proxy: { '/api': 'http://localhost:8080' },
  },
});
