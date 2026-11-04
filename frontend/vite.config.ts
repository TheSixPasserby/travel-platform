import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.TRAVEL_API_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: process.env.TRAVEL_API_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 5173,
    strictPort: true
  },
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.spec.ts'],
    setupFiles: ['./tests/setup.ts'],
    css: false,
    clearMocks: true
  }
})
