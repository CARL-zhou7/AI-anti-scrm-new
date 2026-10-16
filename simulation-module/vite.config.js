import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { mockBackendPlugin } from './dev/mock/server.js'

// 仅用于本模块本地开发与测试；并入主工程后使用主工程自己的构建配置
export default defineConfig({
  plugins: [vue(), mockBackendPlugin()],
  server: { port: 5173, host: '127.0.0.1' },
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.test.js'],
  },
})
