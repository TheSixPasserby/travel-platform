import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5174, strictPort: true,
    fs: { strict: true },
    proxy: {
      '/api': { target: process.env.TRAVEL_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
      '/uploads': { target: process.env.TRAVEL_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true }
    }
  }
})
