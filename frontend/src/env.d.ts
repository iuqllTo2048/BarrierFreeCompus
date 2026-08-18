/// <reference types="vite/client" />
/// <reference types="@amap/amap-jsapi-types" />

interface ImportMetaEnv {
  readonly VITE_AMAP_JS_KEY: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

interface Window {
  _AMapSecurityConfig?: {
    serviceHost?: string;
  };
}
