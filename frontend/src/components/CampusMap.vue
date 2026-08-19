<script setup lang="ts">
import AMapLoader from '@amap/amap-jsapi-loader';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import AppIcon from './AppIcon.vue';
import { appIconPaths, type AppIconName } from '../services/app-icons';
import type { Coordinate, GeoJsonGeometry, MapSnapshot, RouteResult } from '../types/map';

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
  setCenter(center: [number, number], immediately?: boolean): void;
  setZoom(zoom: number, immediately?: boolean): void;
  setMapStyle(style: string): void;
  destroy(): void;
}

interface HeatMapInstance {
  setDataSet(dataSet: {
    data: Array<{ lng: number; lat: number; count: number }>;
    max: number;
  }): void;
  setMap(map: MapInstance | null): void;
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
  HeatMap: new (map: MapInstance, options?: Record<string, unknown>) => HeatMapInstance;
}

const props = withDefaults(
  defineProps<{
    snapshot: MapSnapshot | null;
    editable?: boolean;
    selectedId?: string | null;
    routes?: RouteResult[];
    selectedRouteIndex?: number;
    startNodeId?: string | null;
    endNodeId?: string | null;
    heatPoints?: Array<{ lng: number; lat: number; count: number }>;
    focusCoordinate?: Coordinate | null;
    visibleBarrierIds?: string[] | null;
  }>(),
  {
    editable: false,
    selectedId: null,
    routes: () => [],
    selectedRouteIndex: 0,
    startNodeId: null,
    endNodeId: null,
    heatPoints: () => [],
    focusCoordinate: null,
    visibleBarrierIds: null,
  },
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
const selectedFeature = computed(() => {
  if (!props.snapshot || !props.selectedId) return null;
  const facility = props.snapshot.facilities.find((item) => item.id === props.selectedId);
  if (facility)
    return {
      icon: facilityIcon(facility.facilityType),
      kind: facilityTypeLabel(facility.facilityType),
      title: facility.name,
      status: `${facility.openStatus} · ${confidenceLabel(facility.confidenceLevel)}`,
    };
  const barrier = props.snapshot.barriers.find((item) => item.id === props.selectedId);
  if (barrier)
    return {
      icon: barrierIcon(barrier.barrierType),
      kind: barrierTypeLabel(barrier.barrierType),
      title: barrier.title,
      status: `${barrier.active ? '已生效' : '待核验'} · ${confidenceLabel(barrier.confidenceLevel)}`,
    };
  const node = props.snapshot.nodes.find((item) => item.id === props.selectedId);
  if (node)
    return {
      icon: 'node' as AppIconName,
      kind: '道路节点',
      title: node.name ?? node.externalId,
      status: `${node.active ? '启用' : '停用'} · ${confidenceLabel(node.confidenceLevel)}`,
    };
  const building = props.snapshot.buildings.find((item) => item.id === props.selectedId);
  if (building)
    return {
      icon: 'building' as AppIconName,
      kind: '校园建筑',
      title: building.name,
      status: `${building.active ? '启用' : '停用'} · ${confidenceLabel(building.confidenceLevel)}`,
    };
  const edge = props.snapshot.edges.find((item) => item.id === props.selectedId);
  return edge
    ? {
        icon: 'route' as AppIconName,
        kind: '道路',
        title: edge.name ?? edge.externalId,
        status: `${edge.status === 'ACTIVE' ? '启用' : '停用或封闭'} · ${edge.riskLevel} 风险`,
      }
    : null;
});
let api: AMapApi | null = null;
let map: MapInstance | null = null;
let overlays: unknown[] = [];
let heatMap: HeatMapInstance | null = null;

function coordinates(geometry: GeoJsonGeometry): number[][] {
  return geometry.coordinates as number[][];
}

function polygonCoordinates(geometry: GeoJsonGeometry): number[][] {
  return (geometry.coordinates as number[][][])[0] ?? [];
}

function cssColor(token: string, fallback: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(token).trim() || fallback;
}

function createSvgIcon(name: AppIconName): SVGSVGElement {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '1.8');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  for (const data of appIconPaths[name]) {
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', data);
    svg.append(path);
  }
  return svg;
}

function markerContent(
  icon: AppIconName | null,
  text: string,
  className: string,
  label: string,
  selected = false,
  confidence?: string,
): HTMLElement {
  const element = document.createElement('button');
  element.type = 'button';
  element.className = `map-symbol ${className}${selected ? ' is-selected' : ''}${confidence ? ` confidence-${confidence.toLowerCase()}` : ''}`;
  if (icon) element.append(createSvgIcon(icon));
  else element.textContent = text;
  element.setAttribute('aria-label', label);
  element.setAttribute('title', label);
  if (selected) element.setAttribute('aria-pressed', 'true');
  return element;
}

function facilityIcon(type: string): AppIconName {
  const icons: Record<string, AppIconName> = {
    ACCESSIBLE_ENTRANCE: 'accessible-entrance',
    RAMP: 'ramp',
    ELEVATOR: 'elevator',
    ACCESSIBLE_TOILET: 'toilet',
    REST_AREA: 'rest-area',
    ACCESSIBLE_PARKING: 'parking',
    DROP_OFF_POINT: 'drop-off',
    TRANSIT_BOARDING_POINT: 'transit',
  };
  return icons[type] ?? 'services';
}

function barrierIcon(type: string): AppIconName {
  const icons: Record<string, AppIconName> = {
    STAIRS: 'ramp',
    TEMPORARY_CLOSURE: 'close',
    NARROW_PATH: 'accessible-entrance',
    VEHICLE_BLOCKING: 'parking',
    STEEP_SLOPE: 'ramp',
    ELEVATOR_OUTAGE: 'elevator',
    ENTRANCE_CLOSED: 'accessible-entrance',
  };
  return icons[type] ?? (type === 'CONSTRUCTION' ? 'barrier' : 'warning');
}

function facilityTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    ACCESSIBLE_ENTRANCE: '无障碍入口',
    RAMP: '坡道',
    ELEVATOR: '电梯',
    ACCESSIBLE_TOILET: '无障碍卫生间',
    REST_AREA: '休息点',
    ACCESSIBLE_PARKING: '无障碍停车位',
    DROP_OFF_POINT: '落客点',
    TRANSIT_BOARDING_POINT: '公共交通乘车点',
  };
  return labels[type] ?? '无障碍设施';
}

function barrierTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    STAIRS: '楼梯',
    CONSTRUCTION: '施工',
    TEMPORARY_CLOSURE: '临时封闭',
    DAMAGED_SURFACE: '路面破损',
    NARROW_PATH: '道路狭窄',
    VEHICLE_BLOCKING: '车辆占道',
    STEEP_SLOPE: '陡坡',
    ELEVATOR_OUTAGE: '电梯停运',
    ENTRANCE_CLOSED: '入口关闭',
    WATERLOGGING: '积水',
  };
  return labels[type] ?? '通行障碍';
}

function confidenceLabel(level: string): string {
  return { HIGH: '高可信', MEDIUM: '中可信', LOW: '低可信', UNKNOWN: '未核验' }[level] ?? level;
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
      fillColor: selected
        ? cssColor('--color-secondary', '#5cb8ce')
        : cssColor('--color-primary', '#176b82'),
      fillOpacity: building.active ? 0.18 : 0.06,
      strokeColor: selected
        ? cssColor('--color-focus', '#0b6e99')
        : cssColor('--color-text-secondary', '#60756f'),
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
      strokeColor: closed
        ? cssColor('--color-danger', '#b42318')
        : edge.riskLevel === 'UNKNOWN'
          ? cssColor('--color-unknown', '#667085')
          : cssColor('--color-primary', '#0f766e'),
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

  const amap = api;
  props.routes.forEach((route, index) => {
    const active = index === props.selectedRouteIndex;
    const style = routeStyle(route.profile);
    const overlay = new amap.Polyline({
      path: coordinates(route.geometry),
      strokeColor: style.color,
      strokeWeight: active ? style.weight + 2 : style.weight,
      strokeOpacity: active ? 0.96 : 0.58,
      strokeStyle: style.dashed ? 'dashed' : 'solid',
      lineJoin: 'round',
      lineCap: 'round',
      title: `${profileLabel(route.profile)} · ${route.distanceM} 米 · ${route.riskSummary.level} 风险`,
      zIndex: active ? 170 : 150 + index,
    });
    overlays.push(overlay);
  });

  for (const node of props.snapshot.nodes) {
    const selected = props.selectedId === node.id;
    const overlay = new api.CircleMarker({
      center: [node.lng, node.lat],
      radius: selected ? 8 : 5,
      fillColor: node.active
        ? cssColor('--color-primary', '#0f766e')
        : cssColor('--color-unknown', '#667085'),
      fillOpacity: 1,
      strokeColor: cssColor('--color-surface', '#ffffff'),
      strokeWeight: selected ? 3 : 2,
      title: `${node.name ?? node.externalId} · ${node.active ? '启用' : '停用'}`,
      zIndex: selected ? 110 : 90,
    });
    addSelectable(overlay, 'node', node.id);
  }

  for (const facility of props.snapshot.facilities) {
    const overlay = new api.Marker({
      position: [facility.lng, facility.lat],
      content: markerContent(
        facilityIcon(facility.facilityType),
        '',
        'facility-symbol',
        `${facility.name}，${facilityTypeLabel(facility.facilityType)}，${confidenceLabel(facility.confidenceLevel)}`,
        props.selectedId === facility.id,
        facility.confidenceLevel,
      ),
      offset: new api.Pixel(-17, -17),
      title: `${facility.name} · ${facility.openStatus}`,
      zIndex: props.selectedId === facility.id ? 130 : 100,
    });
    addSelectable(overlay, 'facility', facility.id);
  }

  for (const barrier of props.snapshot.barriers) {
    if (props.visibleBarrierIds && !props.visibleBarrierIds.includes(barrier.id)) continue;
    const point = barrier.geometry.coordinates as number[];
    const overlay = new api.Marker({
      position: [point[0], point[1]],
      content: markerContent(
        barrierIcon(barrier.barrierType),
        '',
        `barrier-symbol ${barrier.active ? 'is-active' : 'is-pending'}`,
        `${barrier.title}，${barrierTypeLabel(barrier.barrierType)}，${barrier.active ? '已生效' : '待核验'}`,
        props.selectedId === barrier.id,
        barrier.confidenceLevel,
      ),
      offset: new api.Pixel(-17, -17),
      title: `${barrier.title} · ${barrier.active ? '已生效' : '待核验'}`,
      zIndex: props.selectedId === barrier.id ? 140 : 120,
    });
    addSelectable(overlay, 'barrier', barrier.id);
  }

  for (const endpoint of [
    { id: props.startNodeId, text: '起', className: 'start-symbol', label: '路线起点' },
    { id: props.endNodeId, text: '终', className: 'end-symbol', label: '路线终点' },
  ]) {
    const node = props.snapshot.nodes.find((item) => item.id === endpoint.id);
    if (!node) continue;
    const overlay = new api.Marker({
      position: [node.lng, node.lat],
      content: markerContent(null, endpoint.text, endpoint.className, endpoint.label),
      offset: new api.Pixel(-16, -16),
      title: `${endpoint.label}：${node.name ?? node.externalId}`,
      zIndex: 190,
    });
    overlays.push(overlay);
  }

  map.add(overlays);
  if (overlays.length) map.setFitView(overlays, false, [48, 48, 48, 48]);
}

function renderHeatMap(): void {
  if (!map || !api) return;
  if (!heatMap) {
    heatMap = new api.HeatMap(map, {
      radius: 28,
      opacity: [0.18, 0.72],
      gradient: {
        0.2: cssColor('--color-secondary', '#5cb8ce'),
        0.55: cssColor('--color-warning', '#fdb022'),
        1: cssColor('--color-danger', '#b42318'),
      },
    });
  }
  heatMap.setDataSet({
    data: props.heatPoints,
    max: Math.max(1, ...props.heatPoints.map((item) => item.count)),
  });
}

function focusMap(): void {
  if (!map || !props.focusCoordinate) return;
  map.setCenter([props.focusCoordinate.lng, props.focusCoordinate.lat], true);
  map.setZoom(18, true);
}

function routeStyle(profile: RouteResult['profile']): {
  color: string;
  weight: number;
  dashed: boolean;
} {
  if (profile === 'ACCESSIBLE')
    return { color: cssColor('--color-primary', '#0f766e'), weight: 6, dashed: false };
  if (profile === 'BALANCED')
    return { color: cssColor('--color-secondary', '#176b82'), weight: 4, dashed: false };
  return { color: cssColor('--color-unknown', '#667085'), weight: 4, dashed: true };
}

function profileLabel(profile: RouteResult['profile']): string {
  if (profile === 'ACCESSIBLE') return '无障碍优先';
  if (profile === 'BALANCED') return '综合路线';
  return '最短路线';
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
      plugins: ['AMap.ToolBar', 'AMap.Scale', 'AMap.HeatMap'],
    })) as unknown as AMapApi;
    const dark = document.documentElement.dataset.theme === 'dark';
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
    renderHeatMap();
    focusMap();
  } catch (reason: unknown) {
    error.value = reason instanceof Error ? reason.message : '高德地图加载失败';
  } finally {
    loading.value = false;
  }
}

function handleThemeChange(): void {
  if (!map) return;
  const dark = document.documentElement.dataset.theme === 'dark';
  map.setMapStyle(dark ? 'amap://styles/dark' : 'amap://styles/normal');
  heatMap?.setMap(null);
  heatMap = null;
  renderSnapshot();
  renderHeatMap();
}

watch(
  () =>
    [
      props.snapshot,
      props.selectedId,
      props.routes,
      props.selectedRouteIndex,
      props.startNodeId,
      props.endNodeId,
      props.heatPoints,
      props.focusCoordinate,
      props.visibleBarrierIds,
    ] as const,
  () =>
    nextTick(() => {
      renderSnapshot();
      renderHeatMap();
      focusMap();
    }),
  { deep: true },
);

onMounted(() => {
  window.addEventListener('theme-change', handleThemeChange);
  void initialize();
});
onBeforeUnmount(() => {
  window.removeEventListener('theme-change', handleThemeChange);
  heatMap?.setMap(null);
  map?.destroy();
});
</script>

<template>
  <div class="campus-map-shell" :class="{ 'is-loading': loading }" :aria-busy="loading">
    <div
      ref="container"
      class="amap-container"
      role="application"
      aria-label="云麓校园地图；地图对象也可通过相邻列表和表单操作"
    />
    <div v-if="loading" class="map-state map-loading-state" role="status">
      <span class="map-loading-line" aria-hidden="true" />
      <strong>正在加载校园地图</strong>
      <span>正在同步底图与无障碍路网…</span>
    </div>
    <div v-else-if="error" class="map-state map-state-error" role="alert">
      <strong>地图暂时无法加载</strong>
      <span>{{ error }}</span>
      <span>数据管理接口仍可使用，请检查本地高德配置。</span>
    </div>
    <aside v-if="selectedFeature" class="map-feature-popup" aria-live="polite">
      <span class="map-feature-popup__icon"><AppIcon :name="selectedFeature.icon" /></span>
      <span>
        <small>{{ selectedFeature.kind }}</small>
        <strong>{{ selectedFeature.title }}</strong>
        <span>{{ selectedFeature.status }}</span>
      </span>
    </aside>
    <div class="map-legend" aria-label="地图图例">
      <span><i class="legend-node" />道路节点</span>
      <span><i class="legend-edge" />启用道路</span>
      <span><i class="legend-edge closed" />封闭/未知</span>
      <span
        ><i class="legend-symbol facility-symbol"><AppIcon name="services" :size="14" /></i
        >设施</span
      >
      <span
        ><i class="legend-symbol barrier-symbol"><AppIcon name="warning" :size="14" /></i>障碍</span
      >
      <template v-if="routes.length">
        <span><i class="legend-route shortest" />最短路线</span>
        <span><i class="legend-route accessible" />无障碍优先</span>
        <span><i class="legend-route balanced" />综合路线</span>
      </template>
    </div>
  </div>
</template>
