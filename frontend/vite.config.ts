import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', '');
  const jsKey = process.env.VITE_AMAP_JS_KEY ?? env.AMAP_JS_KEY ?? '';
  const securityCode = env.AMAP_SECURITY_JS_CODE ?? '';
  const appendSecurityCode = (path: string): string => {
    if (!securityCode) return path;
    const separator = path.includes('?') ? '&' : '?';
    return `${path}${separator}jscode=${encodeURIComponent(securityCode)}`;
  };

  return {
    define: {
      'import.meta.env.VITE_AMAP_JS_KEY': JSON.stringify(jsKey),
    },
    plugins: [vue(), Components({ resolvers: [ElementPlusResolver()] })],
    server: {
      proxy: {
        '/api': {
          target: 'http://localhost:8081',
          changeOrigin: true,
        },
        '/_AMapService/v4/map/styles': {
          target: 'https://webapi.amap.com',
          changeOrigin: true,
          rewrite: (path) => appendSecurityCode(path.replace('/_AMapService', '')),
        },
        '/_AMapService': {
          target: 'https://restapi.amap.com',
          changeOrigin: true,
          rewrite: (path) => appendSecurityCode(path.replace('/_AMapService', '')),
        },
      },
    },
    test: {
      environment: 'node',
      exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
    },
  };
});
