import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// En desarrollo, /api se redirige al backend Spring Boot.
// En produccion (Docker) lo resuelve nginx.
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
