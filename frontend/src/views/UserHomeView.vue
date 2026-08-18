<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import CampusMap from '../components/CampusMap.vue';
import * as businessApi from '../services/business-api';
import { readApiMessage } from '../services/http';
import { planRoutes } from '../services/route-api';
import { useMapDataStore } from '../stores/map-data';
import type {
  MobilityMode,
  RoutePlanResponse,
  RouteProfile,
  RouteResult,
  TravelPeriod,
} from '../types/map';

const mapData = useMapDataStore();
const router = useRouter();
const planning = ref(false);
const result = ref<RoutePlanResponse | null>(null);
const selectedRouteIndex = ref(0);
const selectionTarget = ref<'start' | 'end' | null>(null);
const form = reactive({
  startNodeId: '',
  endNodeId: '',
  mobilityMode: 'WALKING' as MobilityMode,
  travelPeriod: 'DAY' as TravelPeriod,
  avoidStairs: false,
  distanceWeight: 1,
  slopeWeight: 1,
  widthWeight: 1,
  preferRestArea: false,
  preferToilet: false,
});

const nodes = computed(() => mapData.snapshot?.nodes.filter((node) => node.active) ?? []);
const routes = computed(() => result.value?.routes ?? []);
const selectedRoute = computed(() => routes.value[selectedRouteIndex.value] ?? null);
const mobilityOptions: Array<{ value: MobilityMode; label: string }> = [
  { value: 'WHEELCHAIR', label: '轮椅出行' },
  { value: 'CRUTCH', label: '拐杖辅助' },
  { value: 'TEMPORARY_INJURY', label: '临时受伤' },
  { value: 'CART_LUGGAGE', label: '推车 / 大件行李' },
  { value: 'WALKING', label: '普通步行' },
];

function nodeLabel(nodeId: string): string {
  const node = nodes.value.find((item) => item.id === nodeId);
  return node ? `${node.name ?? node.externalId}（${node.externalId}）` : '未选择';
}
function profileLabel(profile: RouteProfile): string {
  if (profile === 'ACCESSIBLE') return '无障碍优先';
  if (profile === 'BALANCED') return '综合路线';
  return '最短路线';
}
function riskLabel(level: RouteResult['riskSummary']['level']): string {
  return { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', UNKNOWN: '含未知风险' }[level];
}
function initializeEndpoints(): void {
  const start = nodes.value.find((node) => node.externalId === 'N02') ?? nodes.value[0];
  const end = nodes.value.find((node) => node.externalId === 'N03') ?? nodes.value.at(-1);
  if (!form.startNodeId && start) form.startNodeId = start.id;
  if (!form.endNodeId && end) form.endNodeId = end.id;
}
async function loadDataset(datasetId?: string): Promise<void> {
  result.value = null;
  if (datasetId) await mapData.select(datasetId, false);
  else await mapData.load(false);
  form.startNodeId = '';
  form.endNodeId = '';
  initializeEndpoints();
}
async function submitPlan(): Promise<void> {
  if (!mapData.snapshot || !form.startNodeId || !form.endNodeId) {
    ElMessage.warning('请先选择起点和终点');
    return;
  }
  planning.value = true;
  try {
    result.value = await planRoutes({
      datasetId: mapData.snapshot.dataset.id,
      startNodeId: form.startNodeId,
      endNodeId: form.endNodeId,
      mobilityMode: form.mobilityMode,
      travelPeriod: form.travelPeriod,
      preferences: {
        avoidStairs: form.avoidStairs,
        distanceWeight: form.distanceWeight,
        slopeWeight: form.slopeWeight,
        widthWeight: form.widthWeight,
        restAreaWeight: form.preferRestArea ? 1.4 : undefined,
        accessibleToiletWeight: form.preferToilet ? 1.4 : undefined,
      },
    });
    const accessible = result.value.routes.findIndex((route) =>
      route.equivalentProfiles.includes('ACCESSIBLE'),
    );
    selectedRouteIndex.value = accessible >= 0 ? accessible : 0;
    if (!result.value.routes.length) ElMessage.warning('当前约束下没有可通行路线');
  } catch (error: unknown) {
    result.value = null;
    ElMessage.error(readApiMessage(error, '路线规划失败，请稍后重试'));
  } finally {
    planning.value = false;
  }
}
function swapEndpoints(): void {
  [form.startNodeId, form.endNodeId] = [form.endNodeId, form.startNodeId];
  result.value = null;
}
async function selectFeature(selection: { kind: string; id: string }): Promise<void> {
  if (selection.kind === 'facility' && !selectionTarget.value) {
    await router.push({ path: '/user/services', query: { facility: selection.id } });
    return;
  }
  if (selection.kind !== 'node' || !selectionTarget.value) return;
  if (selectionTarget.value === 'start') form.startNodeId = selection.id;
  else form.endNodeId = selection.id;
  result.value = null;
  ElMessage.success(
    `已选择${selectionTarget.value === 'start' ? '起点' : '终点'}：${nodeLabel(selection.id)}`,
  );
  selectionTarget.value = null;
}
async function saveCurrentFavorite(): Promise<void> {
  if (!result.value?.historyId || !selectedRoute.value) return;
  const answer = await ElMessageBox.prompt('为这条路线填写收藏名称', '收藏路线', {
    inputValue: `${nodeLabel(form.startNodeId)} → ${nodeLabel(form.endNodeId)}`,
  });
  await businessApi.favoriteHistory(
    result.value.historyId,
    selectedRoute.value.profile,
    answer.value,
  );
  ElMessage.success('路线已收藏');
}

onMounted(async () => {
  await loadDataset();
  try {
    const defaults = await businessApi.getProfile();
    Object.assign(form, {
      mobilityMode: defaults.defaultMobilityMode,
      avoidStairs: defaults.avoidStairs,
      distanceWeight: defaults.distanceWeight,
      slopeWeight: defaults.slopeWeight,
      widthWeight: defaults.widthWeight,
      preferRestArea: defaults.preferRestArea,
      preferToilet: defaults.preferAccessibleToilet,
    });
  } catch {
    // 路线规划仍可使用本页默认值，个人配置失败不阻塞主流程。
  }
});
</script>

<template>
  <section class="route-planning-layout" aria-labelledby="user-title">
    <aside class="route-panel route-control-panel">
      <p class="eyebrow">用户端 · 无障碍路线</p>
      <h1 id="user-title">规划校园通行路线</h1>
      <p class="muted">底图仅用于导览，路线由校园自建路网与可解释 A* 算法生成。</p>
      <label class="field-label" for="user-dataset">当前数据集</label>
      <el-select
        id="user-dataset"
        :model-value="mapData.selectedDatasetId"
        :disabled="mapData.loading || planning"
        @change="(value: string) => loadDataset(value)"
      >
        <el-option
          v-for="dataset in mapData.datasets"
          :key="dataset.id"
          :label="dataset.name"
          :value="dataset.id"
        />
      </el-select>
      <p v-if="mapData.error" class="form-error" role="alert">{{ mapData.error }}</p>

      <div class="endpoint-field">
        <label class="field-label" for="route-start">起点</label>
        <el-button
          text
          :type="selectionTarget === 'start' ? 'primary' : 'default'"
          @click="selectionTarget = selectionTarget === 'start' ? null : 'start'"
          >从地图选择</el-button
        >
      </div>
      <el-select id="route-start" v-model="form.startNodeId" filterable @change="result = null">
        <el-option
          v-for="node in nodes"
          :key="node.id"
          :label="nodeLabel(node.id)"
          :value="node.id"
        />
      </el-select>
      <div class="endpoint-field">
        <label class="field-label" for="route-end">终点</label>
        <el-button
          text
          :type="selectionTarget === 'end' ? 'primary' : 'default'"
          @click="selectionTarget = selectionTarget === 'end' ? null : 'end'"
          >从地图选择</el-button
        >
      </div>
      <el-select id="route-end" v-model="form.endNodeId" filterable @change="result = null">
        <el-option
          v-for="node in nodes"
          :key="node.id"
          :label="nodeLabel(node.id)"
          :value="node.id"
        />
      </el-select>
      <el-button class="swap-button" plain @click="swapEndpoints">交换起点与终点</el-button>

      <div class="two-column-fields">
        <div>
          <label class="field-label" for="mobility-mode">出行方式</label
          ><el-select id="mobility-mode" v-model="form.mobilityMode"
            ><el-option
              v-for="item in mobilityOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
          /></el-select>
        </div>
        <div>
          <label class="field-label" for="travel-period">出行时段</label
          ><el-select id="travel-period" v-model="form.travelPeriod"
            ><el-option label="白天" value="DAY" /><el-option label="夜间" value="NIGHT"
          /></el-select>
        </div>
      </div>

      <details class="preference-panel">
        <summary>个性化偏好</summary>
        <div class="preference-switch">
          <span>尽量避开楼梯</span><el-switch v-model="form.avoidStairs" />
        </div>
        <div class="preference-switch">
          <span>偏好休息点</span><el-switch v-model="form.preferRestArea" />
        </div>
        <div class="preference-switch">
          <span>偏好无障碍卫生间</span><el-switch v-model="form.preferToilet" />
        </div>
        <label
          >距离权重 <strong>{{ form.distanceWeight.toFixed(1) }}</strong></label
        ><el-slider v-model="form.distanceWeight" :min="0.5" :max="2" :step="0.1" />
        <label
          >坡度权重 <strong>{{ form.slopeWeight.toFixed(1) }}</strong></label
        ><el-slider v-model="form.slopeWeight" :min="0.5" :max="2" :step="0.1" />
        <label
          >宽度权重 <strong>{{ form.widthWeight.toFixed(1) }}</strong></label
        ><el-slider v-model="form.widthWeight" :min="0.5" :max="2" :step="0.1" />
      </details>
      <el-button type="primary" class="plan-button" :loading="planning" @click="submitPlan"
        >规划三类路线</el-button
      >
      <p class="coordinate-note">坐标系：GCJ-02 · 风险会同时使用文字、标签与线型表达</p>
    </aside>

    <CampusMap
      :snapshot="mapData.snapshot"
      :routes="routes"
      :selected-route-index="selectedRouteIndex"
      :start-node-id="form.startNodeId"
      :end-node-id="form.endNodeId"
      @feature-select="selectFeature"
    />

    <aside class="route-results-panel" aria-label="路线规划结果">
      <template v-if="result">
        <div class="results-heading">
          <div>
            <p class="eyebrow">规划结果</p>
            <h2>{{ routes.length ? `${routes.length} 条候选路线` : '未找到可通行路线' }}</h2>
          </div>
          <span class="data-badge">{{ result.travelPeriod === 'NIGHT' ? '夜间' : '白天' }}</span>
        </div>
        <div v-if="result.notices.length" class="route-notices" role="status">
          <p v-for="notice in result.notices" :key="notice">{{ notice }}</p>
        </div>
        <div class="route-card-list">
          <button
            v-for="(route, index) in routes"
            :key="route.edgeIds.join('-') || index"
            type="button"
            class="route-card"
            :class="[
              `profile-${route.profile.toLowerCase()}`,
              { selected: selectedRouteIndex === index },
            ]"
            :aria-pressed="selectedRouteIndex === index"
            @click="selectedRouteIndex = index"
          >
            <span class="route-card-heading"
              ><span><i class="route-swatch" />{{ profileLabel(route.profile) }}</span
              ><span class="risk-chip" :class="`risk-${route.riskSummary.level.toLowerCase()}`">{{
                riskLabel(route.riskSummary.level)
              }}</span></span
            >
            <span v-if="route.equivalentProfiles.length > 1" class="equivalent-note"
              >与 {{ route.equivalentProfiles.map(profileLabel).join('、') }} 结果相同</span
            >
            <span class="route-metrics"
              ><strong>{{ Math.round(route.distanceM) }} 米</strong
              ><span>约 {{ route.estimatedMinutes }} 分钟</span
              ><span>{{ route.stairsCount ? `${route.stairsCount} 级楼梯` : '无楼梯' }}</span></span
            >
          </button>
        </div>
        <section v-if="selectedRoute" class="route-details" aria-live="polite">
          <h3>路线说明</h3>
          <ul v-if="selectedRoute.warnings.length" class="warning-list">
            <li v-for="warning in selectedRoute.warnings" :key="warning">{{ warning }}</li>
          </ul>
          <p v-else class="safe-note">✓ 当前路线没有额外警告</p>
          <dl class="route-detail-grid">
            <div>
              <dt>可信度</dt>
              <dd>{{ selectedRoute.confidence }}</dd>
            </div>
            <div>
              <dt>等效成本</dt>
              <dd>{{ selectedRoute.costBreakdown.total.toFixed(1) }}</dd>
            </div>
            <div>
              <dt>搜索节点</dt>
              <dd>{{ selectedRoute.algorithmMetrics.expandedNodes }}</dd>
            </div>
            <div>
              <dt>沿途设施</dt>
              <dd>{{ selectedRoute.facilities.length }}</dd>
            </div>
          </dl>
          <details class="cost-details">
            <summary>查看可解释成本明细</summary>
            <dl>
              <div>
                <dt>距离</dt>
                <dd>{{ selectedRoute.costBreakdown.distance.toFixed(1) }}</dd>
              </div>
              <div>
                <dt>坡度</dt>
                <dd>{{ selectedRoute.costBreakdown.slope.toFixed(1) }}</dd>
              </div>
              <div>
                <dt>楼梯</dt>
                <dd>{{ selectedRoute.costBreakdown.stairs.toFixed(1) }}</dd>
              </div>
              <div>
                <dt>宽度</dt>
                <dd>{{ selectedRoute.costBreakdown.width.toFixed(1) }}</dd>
              </div>
              <div>
                <dt>路面 / 照明</dt>
                <dd>
                  {{
                    (
                      selectedRoute.costBreakdown.surface + selectedRoute.costBreakdown.lighting
                    ).toFixed(1)
                  }}
                </dd>
              </div>
              <div>
                <dt>障碍 / 未知</dt>
                <dd>
                  {{
                    (
                      selectedRoute.costBreakdown.barrier + selectedRoute.costBreakdown.uncertainty
                    ).toFixed(1)
                  }}
                </dd>
              </div>
            </dl>
          </details>
          <el-button type="primary" plain class="action-button" @click="saveCurrentFavorite">
            收藏当前路线
          </el-button>
        </section>
      </template>
      <div v-else class="results-empty">
        <span class="empty-route-symbol" aria-hidden="true">路线</span>
        <h2>等待路线规划</h2>
        <p>选择起终点和出行方式后，系统会同时计算最短、无障碍优先和综合路线。</p>
      </div>
    </aside>
  </section>
</template>
