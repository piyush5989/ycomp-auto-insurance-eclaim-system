import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'path';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      workbox: {
        runtimeCaching: [
          {
            // StaleWhileRevalidate for claim data — show cached, revalidate in background
            urlPattern: /\/api\/v1\/claims\/.*/,
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'claims-cache',
              expiration: { maxAgeSeconds: 3600, maxEntries: 100 }
            }
          },
          {
            // NetworkFirst for claim list — prioritise fresh data
            urlPattern: /\/api\/v1\/claims$/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'claims-list-cache',
              networkTimeoutSeconds: 3,
              expiration: { maxAgeSeconds: 300 }
            }
          }
        ]
      },
      manifest: {
        name: 'eClaims',
        short_name: 'eClaims',
        description: 'Digital insurance claims management platform',
        theme_color: '#1565C0',
        background_color: '#ffffff',
        display: 'standalone',
        icons: [
          { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png' }
        ]
      }
    })
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true
      }
    }
  }
});
