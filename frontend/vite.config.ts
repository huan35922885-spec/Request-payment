import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [
    vue(),
    {
      name: 'dayjs-esm-resolver',
      resolveId(source) {
        if (source === 'dayjs') {
          return fileURLToPath(new URL('./node_modules/dayjs/esm/index.js', import.meta.url))
        }

        if (source.startsWith('dayjs/plugin/')) {
          const pluginName = source.slice('dayjs/plugin/'.length).replace(/\.js$/, '')
          return fileURLToPath(new URL(`./node_modules/dayjs/esm/plugin/${pluginName}`, import.meta.url))
        }

        return undefined
      },
    },
  ],
  resolve: {
    alias: [
      ...[
        'advancedFormat',
        'customParseFormat',
        'dayOfYear',
        'isSameOrAfter',
        'isSameOrBefore',
        'localeData',
        'weekOfYear',
        'weekYear',
      ].map((pluginName) => ({
        find: `dayjs/plugin/${pluginName}.js`,
        replacement: fileURLToPath(new URL(`./node_modules/dayjs/esm/plugin/${pluginName}`, import.meta.url)),
      })),
      {
        find: 'dayjs',
        replacement: fileURLToPath(new URL('./node_modules/dayjs/esm/index.js', import.meta.url)),
      },
    ],
  },
  optimizeDeps: {
    noDiscovery: true,
  },
  server: {
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
      },
    },
  },
})
