import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: { '/anti.scam/v1': { target: 'http://localhost:8080', changeOrigin: false } },
  },
  test: { environment: 'jsdom', clearMocks: true },
})
