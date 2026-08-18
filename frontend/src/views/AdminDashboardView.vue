<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import CampusMap from '../components/CampusMap.vue';
import { useMapDataStore } from '../stores/map-data';
import * as mapApi from '../services/map-api';
import { readApiMessage } from '../services/http';
import type { Coordinate, EdgeRequest, NodeRequest } from '../types/map';

type EditMode = 'SELECT' | 'NODE' | 'EDGE' | 'BUILDING' | 'ENTRANCE' | 'FACILITY' | 'BARRIER';
type FeatureKind = 'node' | 'edge' | 'facility' | 'barrier' | 'building';

const mapData = useMapDataStore();
const mode = ref<EditMode>('SELECT');
const selectedId = ref<string | null>(null);
const editingId = ref<string | null>(null);
const saving = ref(false);
const edgeNodeHint = ref('请选择第一个道路节点');
const fileInput = ref<HTMLInputElement | null>(null);

const nodeForm = reactive<NodeRequest>({
  externalId: '',
  name: '',
  nodeType: 'INTERSECTION',
  active: true,
  coordinate: { lng: 112.9365, lat: 28.1775 },
});

const edgeForm = reactive<EdgeRequest>({
  externalId: '',
  name: '',
  fromNodeId: '',
  toNodeId: '',
  distanceM: 100,
  slopeLevel: 'UNKNOWN',
  hasStairs: false,
  stairsCount: 0,
  widthLevel: 'UNKNOWN',
  surfaceType: 'UNKNOWN',
  lightingLevel: 'UNKNOWN',
  bidirectional: true,
  status: 'ACTIVE',
  riskLevel: 'UNKNOWN',
  intermediatePoints: [],
});
const intermediateText = ref('');

const pointForm = reactive({
  externalId: '',
  name: '',
  category: 'TEACHING',
  buildingId: '',
  facilityType: 'RAMP',
  floorLabel: '',
  openStatus: 'OPEN',
  description: '',
  accessible: true,
  entranceType: 'ACCESSIBLE',
  barrierType: 'CONSTRUCTION',
  reviewStatus: 'PENDING',
  active: true,
  coordinate: { lng: 112.9365, lat: 28.1775 } as Coordinate,
});

const selectionOptions = computed(() => {
  if (!mapData.snapshot) return [];
  return [
    ...mapData.snapshot.nodes.map((item) => ({
      value: `node:${item.id}`,
      label: `节点 · ${item.name ?? item.externalId}`,
    })),
    ...mapData.snapshot.edges.map((item) => ({
      value: `edge:${item.id}`,
      label: `道路 · ${item.name ?? item.externalId}`,
    })),
    ...mapData.snapshot.facilities.map((item) => ({
      value: `facility:${item.id}`,
      label: `设施 · ${item.name}`,
    })),
    ...mapData.snapshot.barriers.map((item) => ({
      value: `barrier:${item.id}`,
      label: `障碍 · ${item.title}`,
    })),
    ...mapData.snapshot.buildings.map((item) => ({
      value: `building:${item.id}`,
      label: `建筑 · ${item.name}`,
    })),
  ];
});

const datasetCounts = computed(() => {
  const snapshot = mapData.snapshot;
  return snapshot
    ? `${snapshot.nodes.length} 节点 · ${snapshot.edges.length} 道路 · ${snapshot.facilities.length} 设施`
    : '正在读取数据';
});

function uniqueExternalId(prefix: string): string {
  return `${prefix}-${Date.now().toString(36).toUpperCase()}`;
}

function beginMode(next: EditMode): void {
  mode.value = next;
  selectedId.value = null;
  editingId.value = null;
  if (next === 'NODE') {
    Object.assign(nodeForm, {
      externalId: uniqueExternalId('N-MAN'),
      name: '新道路节点',
      nodeType: 'INTERSECTION',
      active: true,
      coordinate: {
        lng: mapData.selectedDataset?.centerLng ?? 112.9365,
        lat: mapData.selectedDataset?.centerLat ?? 28.1775,
      },
    });
  } else if (next === 'EDGE') {
    Object.assign(edgeForm, {
      externalId: uniqueExternalId('E-MAN'),
      name: '新道路',
      fromNodeId: '',
      toNodeId: '',
      distanceM: 100,
      slopeLevel: 'UNKNOWN',
      hasStairs: false,
      stairsCount: 0,
      widthLevel: 'UNKNOWN',
      surfaceType: 'UNKNOWN',
      lightingLevel: 'UNKNOWN',
      bidirectional: true,
      status: 'ACTIVE',
      riskLevel: 'UNKNOWN',
      intermediatePoints: [],
    });
    intermediateText.value = '';
    edgeNodeHint.value = '请选择第一个道路节点';
  } else if (next !== 'SELECT') {
    Object.assign(pointForm, {
      externalId: uniqueExternalId(next.slice(0, 3)),
      name: `新${modeLabel(next)}`,
      description: '',
      active: true,
      coordinate: {
        lng: mapData.selectedDataset?.centerLng ?? 112.9365,
        lat: mapData.selectedDataset?.centerLat ?? 28.1775,
      },
    });
  }
}

function modeLabel(value: EditMode): string {
  return {
    SELECT: '查看对象',
    NODE: '节点',
    EDGE: '道路',
    BUILDING: '建筑',
    ENTRANCE: '入口',
    FACILITY: '设施',
    BARRIER: '障碍',
  }[value];
}

function mapClick(coordinate: Coordinate): void {
  if (mode.value === 'NODE') nodeForm.coordinate = coordinate;
  if (['BUILDING', 'ENTRANCE', 'FACILITY', 'BARRIER'].includes(mode.value)) {
    pointForm.coordinate = coordinate;
  }
}

function haversineMeters(first: Coordinate, second: Coordinate): number {
  const radians = (degrees: number): number => (degrees * Math.PI) / 180;
  const earthRadius = 6_371_000;
  const deltaLat = radians(second.lat - first.lat);
  const deltaLng = radians(second.lng - first.lng);
  const a =
    Math.sin(deltaLat / 2) ** 2 +
    Math.cos(radians(first.lat)) * Math.cos(radians(second.lat)) * Math.sin(deltaLng / 2) ** 2;
  return Math.round(earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
}

function selectFeature(selection: { kind: FeatureKind; id: string }): void {
  selectedId.value = selection.id;
  const snapshot = mapData.snapshot;
  if (!snapshot) return;
  if (mode.value === 'EDGE' && !editingId.value && selection.kind === 'node') {
    if (!edgeForm.fromNodeId) {
      edgeForm.fromNodeId = selection.id;
      edgeNodeHint.value = '已选择起点，请选择第二个道路节点';
    } else if (selection.id !== edgeForm.fromNodeId) {
      edgeForm.toNodeId = selection.id;
      const start = snapshot.nodes.find((item) => item.id === edgeForm.fromNodeId);
      const end = snapshot.nodes.find((item) => item.id === edgeForm.toNodeId);
      if (start && end) edgeForm.distanceM = haversineMeters(start, end);
      edgeNodeHint.value = '起终点已选择，可填写属性并保存';
    }
    return;
  }
  if (selection.kind === 'node') {
    const item = snapshot.nodes.find((candidate) => candidate.id === selection.id);
    if (!item) return;
    mode.value = 'NODE';
    editingId.value = item.id;
    Object.assign(nodeForm, {
      externalId: item.externalId,
      name: item.name ?? '',
      nodeType: item.nodeType,
      active: item.active,
      coordinate: { lng: item.lng, lat: item.lat },
    });
  } else if (selection.kind === 'edge') {
    const item = snapshot.edges.find((candidate) => candidate.id === selection.id);
    if (!item) return;
    mode.value = 'EDGE';
    editingId.value = item.id;
    Object.assign(edgeForm, {
      externalId: item.externalId,
      name: item.name ?? '',
      fromNodeId: item.fromNodeId,
      toNodeId: item.toNodeId,
      distanceM: Number(item.distanceM),
      slopeLevel: item.slopeLevel,
      hasStairs: item.hasStairs,
      stairsCount: item.stairsCount,
      widthLevel: item.widthLevel,
      surfaceType: item.surfaceType,
      lightingLevel: item.lightingLevel,
      bidirectional: item.bidirectional,
      status: item.status,
      riskLevel: item.riskLevel,
    });
    const line = item.geometry.coordinates as number[][];
    intermediateText.value = line
      .slice(1, -1)
      .map((point) => `${point[0]},${point[1]}`)
      .join('\n');
  }
}

function selectFromList(value: string): void {
  const [kind, id] = value.split(':');
  selectFeature({ kind: kind as FeatureKind, id });
}

function parseIntermediatePoints(): Coordinate[] {
  if (!intermediateText.value.trim()) return [];
  return intermediateText.value.split(/\r?\n/).map((line) => {
    const [lng, lat] = line.split(',').map(Number);
    if (!Number.isFinite(lng) || !Number.isFinite(lat))
      throw new Error('中间点格式应为：经度,纬度；每行一个');
    return { lng, lat };
  });
}

async function saveCurrent(): Promise<void> {
  const datasetId = mapData.selectedDatasetId;
  if (!datasetId) return;
  saving.value = true;
  try {
    if (mode.value === 'NODE') {
      await mapApi.saveNode(datasetId, nodeForm, editingId.value ?? undefined);
    } else if (mode.value === 'EDGE') {
      if (!edgeForm.fromNodeId || !edgeForm.toNodeId) throw new Error('请先选择道路起点和终点');
      edgeForm.intermediatePoints = parseIntermediatePoints();
      await mapApi.saveEdge(datasetId, edgeForm, editingId.value ?? undefined);
    } else {
      await savePointObject(datasetId);
    }
    await mapData.refresh(true);
    ElMessage.success(`${modeLabel(mode.value)}已保存并持久化`);
    mode.value = 'SELECT';
    editingId.value = null;
  } catch (reason: unknown) {
    ElMessage.error(reason instanceof Error ? reason.message : readApiMessage(reason, '保存失败'));
  } finally {
    saving.value = false;
  }
}

async function savePointObject(datasetId: string): Promise<void> {
  const common = { externalId: pointForm.externalId, coordinate: pointForm.coordinate };
  if (mode.value === 'BUILDING') {
    await mapApi.createMapObject(datasetId, 'buildings', {
      ...common,
      name: pointForm.name,
      category: pointForm.category,
      description: pointForm.description,
      active: pointForm.active,
    });
  } else if (mode.value === 'ENTRANCE') {
    if (!pointForm.buildingId) throw new Error('请选择入口所属建筑');
    await mapApi.createMapObject(datasetId, 'entrances', {
      ...common,
      buildingId: pointForm.buildingId,
      name: pointForm.name,
      accessible: pointForm.accessible,
      entranceType: pointForm.entranceType,
      status: pointForm.openStatus,
      active: pointForm.active,
    });
  } else if (mode.value === 'FACILITY') {
    await mapApi.createMapObject(datasetId, 'facilities', {
      ...common,
      buildingId: pointForm.buildingId || null,
      name: pointForm.name,
      facilityType: pointForm.facilityType,
      floorLabel: pointForm.floorLabel || null,
      openStatus: pointForm.openStatus,
      description: pointForm.description,
      active: pointForm.active,
    });
  } else if (mode.value === 'BARRIER') {
    await mapApi.createMapObject(datasetId, 'barriers', {
      ...common,
      title: pointForm.name,
      barrierType: pointForm.barrierType,
      description: pointForm.description,
      reviewStatus: pointForm.reviewStatus,
      active: pointForm.active,
    });
  }
}

async function toggleDataset(value: string | number | boolean): Promise<void> {
  if (!mapData.selectedDatasetId) return;
  const enabled = value === true;
  await mapApi.setDatasetEnabled(mapData.selectedDatasetId, enabled);
  await mapData.load(true);
  ElMessage.success(enabled ? '数据集已启用' : '数据集已停用，用户端将不再读取');
}

async function downloadGeoJson(): Promise<void> {
  if (!mapData.selectedDatasetId) return;
  const data = await mapApi.exportGeoJson(mapData.selectedDatasetId);
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(data, null, 2)], { type: 'application/geo+json' }),
  );
  const link = document.createElement('a');
  link.href = url;
  link.download = `${mapData.selectedDataset?.code ?? 'dataset'}.geojson`;
  link.click();
  URL.revokeObjectURL(url);
}

async function importFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || !mapData.selectedDatasetId) return;
  try {
    const payload = JSON.parse(await file.text()) as Awaited<
      ReturnType<typeof mapApi.exportGeoJson>
    >;
    const result = await mapApi.importGeoJson(mapData.selectedDatasetId, payload);
    await mapData.refresh(true);
    ElMessage.success(
      `导入完成：${result.nodes} 节点、${result.edges} 道路、${result.facilities} 设施`,
    );
  } catch (reason: unknown) {
    ElMessage.error(readApiMessage(reason, 'GeoJSON 文件无效或与数据集不匹配'));
  } finally {
    input.value = '';
  }
}

onMounted(() => mapData.load(true));
</script>

<template>
  <section class="admin-map-page" aria-labelledby="admin-title">
    <header class="admin-map-heading">
      <div>
        <p class="eyebrow">管理端 · 地图数据治理</p>
        <h1 id="admin-title">云麓校园地图编辑器</h1>
      </div>
      <div class="dataset-actions">
        <el-select
          aria-label="管理数据集"
          :model-value="mapData.selectedDatasetId"
          @change="(value: string) => mapData.select(value, true)"
        >
          <el-option
            v-for="dataset in mapData.datasets"
            :key="dataset.id"
            :label="dataset.name"
            :value="dataset.id"
          />
        </el-select>
        <el-switch
          v-if="mapData.selectedDataset"
          :model-value="mapData.selectedDataset.enabled"
          active-text="已启用"
          inactive-text="已停用"
          @change="toggleDataset"
        />
        <el-button @click="downloadGeoJson">导出 GeoJSON</el-button>
        <el-button @click="fileInput?.click()">导入 GeoJSON</el-button>
        <input
          ref="fileInput"
          class="visually-hidden"
          type="file"
          accept=".json,.geojson,application/geo+json"
          @change="importFile"
        />
      </div>
    </header>

    <div class="editor-toolbar" role="toolbar" aria-label="地图编辑工具">
      <el-button :type="mode === 'SELECT' ? 'primary' : 'default'" @click="beginMode('SELECT')">
        查看/编辑
      </el-button>
      <el-button :type="mode === 'NODE' ? 'primary' : 'default'" @click="beginMode('NODE')">
        新增节点
      </el-button>
      <el-button :type="mode === 'EDGE' ? 'primary' : 'default'" @click="beginMode('EDGE')">
        创建道路
      </el-button>
      <el-button :type="mode === 'BUILDING' ? 'primary' : 'default'" @click="beginMode('BUILDING')">
        新增建筑
      </el-button>
      <el-button :type="mode === 'ENTRANCE' ? 'primary' : 'default'" @click="beginMode('ENTRANCE')">
        新增入口
      </el-button>
      <el-button :type="mode === 'FACILITY' ? 'primary' : 'default'" @click="beginMode('FACILITY')">
        新增设施
      </el-button>
      <el-button :type="mode === 'BARRIER' ? 'primary' : 'default'" @click="beginMode('BARRIER')">
        新增障碍
      </el-button>
      <span class="toolbar-summary">{{ datasetCounts }}</span>
    </div>

    <div class="map-editor-grid">
      <CampusMap
        :snapshot="mapData.snapshot"
        :selected-id="selectedId"
        editable
        @map-click="mapClick"
        @feature-select="selectFeature"
      />

      <aside class="data-inspector" aria-labelledby="inspector-title">
        <div class="inspector-heading">
          <div>
            <p class="eyebrow">数据检查器</p>
            <h2 id="inspector-title">{{ modeLabel(mode) }}</h2>
          </div>
          <span class="data-badge unknown">GCJ-02 · 未核验</span>
        </div>

        <label class="field-label" for="object-select">键盘选择地图对象</label>
        <el-select
          id="object-select"
          filterable
          placeholder="搜索节点、道路、设施…"
          @change="selectFromList"
        >
          <el-option
            v-for="option in selectionOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>

        <div v-if="mode === 'SELECT'" class="inspector-empty">
          <strong>选择地图对象查看或编辑</strong>
          <p>可以点击地图，也可以使用上方可搜索下拉框。所有创建操作都支持直接填写坐标。</p>
        </div>

        <el-form v-else-if="mode === 'NODE'" label-position="top" @submit.prevent="saveCurrent">
          <el-form-item label="外部编号"><el-input v-model="nodeForm.externalId" /></el-form-item>
          <el-form-item label="节点名称"><el-input v-model="nodeForm.name" /></el-form-item>
          <el-form-item label="节点类型">
            <el-select v-model="nodeForm.nodeType">
              <el-option label="交叉点" value="INTERSECTION" /><el-option
                label="入口"
                value="ENTRANCE"
              /><el-option label="途经点" value="WAYPOINT" /><el-option
                label="设施连接点"
                value="FACILITY_CONNECTOR"
              />
            </el-select>
          </el-form-item>
          <div class="coordinate-grid">
            <el-form-item label="经度">
              <el-input-number
                v-model="nodeForm.coordinate.lng"
                :precision="7"
                :step="0.0001"
              /> </el-form-item
            ><el-form-item label="纬度">
              <el-input-number v-model="nodeForm.coordinate.lat" :precision="7" :step="0.0001" />
            </el-form-item>
          </div>
          <el-form-item label="节点状态">
            <el-switch v-model="nodeForm.active" active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-button native-type="submit" type="primary" :loading="saving">
            {{ editingId ? '保存节点修改' : '创建节点' }}
          </el-button>
        </el-form>

        <el-form v-else-if="mode === 'EDGE'" label-position="top" @submit.prevent="saveCurrent">
          <p class="interaction-hint" role="status">{{ edgeNodeHint }}</p>
          <el-form-item label="道路编号"><el-input v-model="edgeForm.externalId" /></el-form-item>
          <el-form-item label="道路名称"><el-input v-model="edgeForm.name" /></el-form-item>
          <div class="coordinate-grid">
            <el-form-item label="起点">
              <el-select v-model="edgeForm.fromNodeId" filterable>
                <el-option
                  v-for="node in mapData.snapshot?.nodes"
                  :key="node.id"
                  :label="node.name ?? node.externalId"
                  :value="node.id"
                />
              </el-select> </el-form-item
            ><el-form-item label="终点">
              <el-select v-model="edgeForm.toNodeId" filterable>
                <el-option
                  v-for="node in mapData.snapshot?.nodes"
                  :key="node.id"
                  :label="node.name ?? node.externalId"
                  :value="node.id"
                />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="折线中间点">
            <el-input
              v-model="intermediateText"
              type="textarea"
              :rows="2"
              placeholder="经度,纬度；每行一个（可选）"
            />
          </el-form-item>
          <div class="coordinate-grid">
            <el-form-item label="距离（米）">
              <el-input-number v-model="edgeForm.distanceM" :min="1" /> </el-form-item
            ><el-form-item label="坡度">
              <el-select v-model="edgeForm.slopeLevel">
                <el-option
                  v-for="value in ['FLAT', 'GENTLE', 'MODERATE', 'STEEP', 'UNKNOWN']"
                  :key="value"
                  :label="value"
                  :value="value"
                />
              </el-select>
            </el-form-item>
          </div>
          <div class="coordinate-grid">
            <el-form-item label="道路宽度">
              <el-select v-model="edgeForm.widthLevel">
                <el-option
                  v-for="value in ['NARROW', 'STANDARD', 'WIDE', 'UNKNOWN']"
                  :key="value"
                  :label="value"
                  :value="value"
                />
              </el-select> </el-form-item
            ><el-form-item label="路面">
              <el-select v-model="edgeForm.surfaceType">
                <el-option
                  v-for="value in ['ASPHALT', 'CONCRETE', 'BRICK', 'GRAVEL', 'DIRT', 'UNKNOWN']"
                  :key="value"
                  :label="value"
                  :value="value"
                />
              </el-select>
            </el-form-item>
          </div>
          <div class="coordinate-grid">
            <el-form-item label="照明">
              <el-select v-model="edgeForm.lightingLevel">
                <el-option
                  v-for="value in ['NONE', 'LOW', 'MEDIUM', 'HIGH', 'UNKNOWN']"
                  :key="value"
                  :label="value"
                  :value="value"
                />
              </el-select> </el-form-item
            ><el-form-item label="风险">
              <el-select v-model="edgeForm.riskLevel">
                <el-option
                  v-for="value in ['LOW', 'MEDIUM', 'HIGH', 'UNKNOWN']"
                  :key="value"
                  :label="value"
                  :value="value"
                />
              </el-select>
            </el-form-item>
          </div>
          <div class="coordinate-grid">
            <el-form-item label="状态">
              <el-select v-model="edgeForm.status">
                <el-option label="启用" value="ACTIVE" /><el-option
                  label="停用"
                  value="INACTIVE"
                /><el-option label="封闭" value="CLOSED" /><el-option
                  label="动态阻断"
                  value="BLOCKED"
                />
              </el-select> </el-form-item
            ><el-form-item label="双向">
              <el-switch v-model="edgeForm.bidirectional" />
            </el-form-item>
          </div>
          <div class="coordinate-grid">
            <el-form-item label="包含楼梯"><el-switch v-model="edgeForm.hasStairs" /></el-form-item
            ><el-form-item label="楼梯级数">
              <el-input-number
                v-model="edgeForm.stairsCount"
                :min="0"
                :disabled="!edgeForm.hasStairs"
              />
            </el-form-item>
          </div>
          <el-button native-type="submit" type="primary" :loading="saving">
            {{ editingId ? '保存道路修改' : '创建道路' }}
          </el-button>
        </el-form>

        <el-form v-else label-position="top" @submit.prevent="saveCurrent">
          <p class="interaction-hint">点击地图确定位置，或直接填写经纬度。</p>
          <el-form-item label="外部编号"><el-input v-model="pointForm.externalId" /></el-form-item>
          <el-form-item :label="mode === 'BARRIER' ? '障碍标题' : `${modeLabel(mode)}名称`">
            <el-input v-model="pointForm.name" />
          </el-form-item>
          <el-form-item v-if="mode === 'BUILDING'" label="建筑类别">
            <el-input v-model="pointForm.category" />
          </el-form-item>
          <el-form-item v-if="mode === 'ENTRANCE' || mode === 'FACILITY'" label="所属建筑">
            <el-select v-model="pointForm.buildingId" clearable filterable>
              <el-option
                v-for="building in mapData.snapshot?.buildings"
                :key="building.id"
                :label="building.name"
                :value="building.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="mode === 'ENTRANCE'" label="入口类型">
            <el-input v-model="pointForm.entranceType" />
          </el-form-item>
          <el-form-item v-if="mode === 'ENTRANCE'" label="无障碍入口">
            <el-switch v-model="pointForm.accessible" />
          </el-form-item>
          <el-form-item v-if="mode === 'FACILITY'" label="设施类型">
            <el-select v-model="pointForm.facilityType">
              <el-option
                v-for="value in [
                  'ACCESSIBLE_ENTRANCE',
                  'RAMP',
                  'ELEVATOR',
                  'ACCESSIBLE_TOILET',
                  'REST_AREA',
                  'ACCESSIBLE_PARKING',
                  'DROP_OFF_POINT',
                  'TRANSIT_BOARDING_POINT',
                ]"
                :key="value"
                :label="value"
                :value="value"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="mode === 'FACILITY'" label="楼层">
            <el-input v-model="pointForm.floorLabel" />
          </el-form-item>
          <el-form-item v-if="mode === 'BARRIER'" label="障碍类型">
            <el-select v-model="pointForm.barrierType">
              <el-option
                v-for="value in [
                  'STAIRS',
                  'CONSTRUCTION',
                  'TEMPORARY_CLOSURE',
                  'DAMAGED_SURFACE',
                  'NARROW_PATH',
                  'VEHICLE_BLOCKING',
                  'STEEP_SLOPE',
                  'ELEVATOR_OUTAGE',
                  'ENTRANCE_CLOSED',
                  'WATERLOGGING',
                ]"
                :key="value"
                :label="value"
                :value="value"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="mode === 'BARRIER'" label="审核状态">
            <el-select v-model="pointForm.reviewStatus">
              <el-option label="待审核" value="PENDING" /><el-option
                label="待核验"
                value="NEEDS_VERIFICATION"
              /><el-option label="已通过" value="APPROVED" /><el-option
                label="已拒绝"
                value="REJECTED"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="mode === 'ENTRANCE' || mode === 'FACILITY'" label="开放状态">
            <el-select v-model="pointForm.openStatus">
              <el-option label="开放" value="OPEN" /><el-option
                label="关闭"
                value="CLOSED"
              /><el-option label="未知" value="UNKNOWN" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="mode !== 'ENTRANCE'" label="说明">
            <el-input v-model="pointForm.description" type="textarea" :rows="2" />
          </el-form-item>
          <div class="coordinate-grid">
            <el-form-item label="经度">
              <el-input-number
                v-model="pointForm.coordinate.lng"
                :precision="7"
                :step="0.0001"
              /> </el-form-item
            ><el-form-item label="纬度">
              <el-input-number v-model="pointForm.coordinate.lat" :precision="7" :step="0.0001" />
            </el-form-item>
          </div>
          <el-form-item label="立即启用"><el-switch v-model="pointForm.active" /></el-form-item>
          <el-button native-type="submit" type="primary" :loading="saving">
            保存{{ modeLabel(mode) }}
          </el-button>
        </el-form>
      </aside>
    </div>
  </section>
</template>
