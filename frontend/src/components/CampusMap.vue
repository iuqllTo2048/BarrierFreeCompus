<script setup lang="ts">
import AMapLoader from '@amap/amap-jsapi-loader';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { Coordinate, GeoJsonGeometry, MapSnapshot } from '../types/map';

interface LngLatValue {
  getLng(): number;
  getLat(): number;
}

interface MapClickEvent {
  lnglat: LngLatValue;
}

interface SelectableOverlay {
  on(event: 'click', handler: () => void): void;
}

interface MapInstance {
  on(event: 'click', handler: (event: MapClickEvent) => void): void;
  add(overlays: unknown[]): void;
  remove(overlays: unknown[]): void;
  setFitView(overlays?: unknown[], immediately?: boolean, avoid?: number[]): void;
  destroy(): void;
}

interface AMapApi {
  Map: new (
    container: HTMLElement,
    options: { center: [number, number]; zoom: number; viewMode: string; mapStyle?: string },
  ) => MapInstance;
  Polygon: new (options: Record<string, unknown>) => SelectableOverlay;
  Polyline: new (options: Record<string, unknown>) => SelectableOverlay;
  CircleMarker: new (options: Record<string, unknown>) => SelectableOverlay;
  Marker: new (options: Record<string, unknown>) => SelectableOverlay;
  Pixel: new (x: number, y: number) => unknown;
  ToolBar: new (options?: Record<string, unknown>) => unknown;
  Scale: new (options?: Record<string, unknown>) => unknown;
}

const props = withDefaults(
  defineProps<{
    snapshot: MapSnapshot | null;
    editable?: boolean;
    selectedId?: string | null;
  }>(),
  { editable: false, selectedId: null },
);

const emit = defineEmits<{
  mapClick: [coordinate: Coordinate];
  featureSelect: [
    selection: { kind: 'node' | 'edge' | 'facility' | 'barrier' | 'building'; id: string },
  ];
}>();

const container = ref<HTMLElement | null>(null);
const loading = ref(true);
const error = ref('');
const configured = computed(() => Boolean(import.meta.env.VITE_AMAP_JS_KEY));
let api: AMapApi | null = null;
let map: MapInstance | null = null;
let overlays: unknown[] = [];

function coordinates(geometry: GeoJsonGeometry): number[][] {
  return geometry.coordinates as number[][];
}

function polygonCoordinates(geometry: GeoJsonGeometry): number[][] {
  return (geometry.coordinates as number[][][])[0] ?? [];
}

function markerContent(text: string, className: string, label: string): HTMLElement {
  const element = document.createElement('button');
  element.type = 'button';
  element.className = `map-symbol ${className}`;
  element.textContent = text;
  element.setAttribute('aria-label', label);
  return element;
}

function addSelectable(
  overlay: SelectableOverlay,
  kind: 'node' | 'edge' | 'facility' | 'barrier' | 'building',
  id: string,
): void {
  overlay.on('click', () => emit('featureSelect', { kind, id }));
  overlays.push(overlay);
}

function renderSnapshot(): void {
  if (!map || !api || !props.snapshot) return;
  if (overlays.length) map.remove(overlays);
  overlays = [];

  for (const building of props.snapshot.buildings) {
    const selected = props.selectedId === building.id;
    const overlay = new api.Polygon({
      path: polygonCoordinates(building.geometry),
      fillColor: selected ? '#5cb8ce' : '#176b82',
      fillOpacity: building.active ? 0.18 : 0.06,
      strokeColor: selected ? '#0b6e99' : '#60756f',
      strokeWeight: selected ? 3 : 1.5,
      strokeStyle: building.active ? 'solid' : 'dashed',
      title: `${building.name} · ${building.confidenceLevel === 'UNKNOWN' ? '未核验' : building.confidenceLevel}`,
      zIndex: 20,
    });
    addSelectable(overlay, 'building', building.id);
  }

  for (const edge of props.snapshot.edges) {
    const selected = props.selectedId === edge.id;
    const closed = edge.status !== 'ACTIVE';
    const overlay = new api.Polyline({
      path: coordinates(edge.geometry),
      strokeColor: closed ? '#b42318' : edge.riskLevel === 'UNKNOWN' ? '#667085' : '#0f766e',
      strokeWeight: selected ? 7 : 4,
      strokeOpacity: edge.status === 'INACTIVE' ? 0.45 : 0.9,
      strokeStyle: closed || edge.riskLevel === 'UNKNOWN' ? 'dashed' : 'solid',
      lineJoin: 'round',
      lineCap: 'round',
      title: `${edge.name ?? edge.externalId} · ${closed ? '停用或封闭' : `${edge.riskLevel} 风险`}`,
      zIndex: selected ? 70 : 40,
    });
    addSelectable(overlay, 'edge', edge.id);
  }

  for (const node of props.snapshot.nodes) {
    const selected = props.selectedId === node.id;
    const overlay = new api.CircleMarker({
      center: [node.lng, node.lat],
      radius: selected ? 8 : 5,
      fillColor: node.active ? '#0f766e' : '#667085',
      fillOpacity: 1,
      strokeColor: '#ffffff',
      strokeWeight: selected ? 3 : 2,
      title: `${node.name ?? node.externalId} · ${node.active ? '启用' : '停用'}`,
      zIndex: selected ? 110 : 90,
    });
    addSelectable(overlay, 'node', node.id);
  }

  for (const facility of props.snapshot.facilities) {
    const overlay = new api.Marker({
      position: [facility.lng, facility.lat],
      content: markerContent('设', 'facility-symbol', `${facility.name}，设施`),
      offset: new api.Pixel(-14, -14),
      title: `${facility.name} · ${facility.openStatus}`,
      zIndex: props.selectedId === facility.id ? 130 : 100,
    });
    addSelectable(overlay, 'facility', facility.id);
  }

  for (const barrier of props.snapshot.barriers) {
    const point = barrier.geometry.coordinates as number[];
    const overlay = new api.Marker({
      position: [point[0], point[1]],
      content: markerContent('障', 'barrier-symbol', `${barrier.title}，障碍`),
      offset: new api.Pixel(-14, -14),
      title: `${barrier.title} · ${barrier.active ? '已生效' : '待核验'}`,
      zIndex: props.selectedId === barrier.id ? 140 : 120,
    });
    addSelectable(overlay, 'barrier', barrier.id);
  }

  map.add(overlays);
  if (overlays.length) map.setFitView(overlays, false, [48, 48, 48, 48]);
}

async function initialize(): Promise<void> {
  if (!container.value || !configured.value) {
    loading.value = false;
    error.value = '尚未配置高德 Web JS API Key';
    return;
  }
  try {
    window._AMapSecurityConfig = { serviceHost: '/_AMapService' };
    api = (await AMapLoader.load({
      key: import.meta.env.VITE_AMAP_JS_KEY,
      version: '2.0',
      plugins: ['AMap.ToolBar', 'AMap.Scale'],
    })) as unknown as AMapApi;
    const dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const center = props.snapshot
      ? [props.snapshot.dataset.centerLng, props.snapshot.dataset.centerLat]
      : [112.9365, 28.1775];
    map = new api.Map(container.value, {
      center: center as [number, number],
      zoom: 17,
      viewMode: '2D',
      mapStyle: dark ? 'amap://styles/dark' : 'amap://styles/normal',
    });
    map.add([new api.ToolBar({ position: 'RB' }), new api.Scale()]);
    map.on('click', (event) => {
      if (props.editable)
        emit('mapClick', { lng: event.lnglat.getLng(), lat: event.lnglat.getLat() });
    });
    renderSnapshot();
  } catch (reason: unknown) {
    error.value = reason instanceof Error ? reason.message : '高德地图加载失败';
  } finally {
    loading.value = false;
  }
}

watch(
  () => [props.snapshot, props.selectedId] as const,
  () => nextTick(renderSnapshot),
  { deep: true },
);

onMounted(initialize);
onBeforeUnmount(() => map?.destroy());
</script>

<template>
  <div class="campus-map-shell">
    <div
      ref="container"
      class="amap-container"
      role="application"
      aria-label="云麓校园地图；地图对象也可通过相邻列表和表单操作"
    />
    <div v-if="loading" class="map-state" role="status">正在加载校园地图…</div>
    <div v-else-if="error" class="map-state map-state-error" role="alert">
      <strong>地图暂时无法加载</strong>
      <span>{{ error }}</span>
      <span>数据管理接口仍可使用，请检查本地高德配置。</span>
    </div>
    <div class="map-legend" aria-label="地图图例">
      <span><i class="legend-node" />道路节点</span>
      <span><i class="legend-edge" />启用道路</span>
      <span><i class="legend-edge closed" />封闭/未知</span>
      <span><i class="legend-symbol facility-symbol">设</i>设施</span>
      <span><i class="legend-symbol barrier-symbol">障</i>障碍</span>
    </div>
  </div>
</template>
