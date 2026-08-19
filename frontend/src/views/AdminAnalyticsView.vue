<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import CampusMap from '../components/CampusMap.vue';
import EChartPanel from '../components/EChartPanel.vue';
import { useMapDataStore } from '../stores/map-data';
import * as analyticsApi from '../services/analytics-api';
import {
  buildingScoreOption,
  confidenceOption,
  facilityOption,
  routeRiskOption,
  trendOption,
  type ChartPalette,
} from '../services/analytics-charts';
import { readApiMessage } from '../services/http';
import { useTheme } from '../services/theme';
import type { AnalyticsFilter, AnalyticsOverview, GovernanceSummary } from '../types/analytics';

const mapData = useMapDataStore();
const { theme } = useTheme();
const loading = ref(false);
const exporting = ref(false);
const summaryLoading = ref(false);
const overview = ref<AnalyticsOverview | null>(null);
const governanceSummary = ref<GovernanceSummary | null>(null);
const inspectorCollapsed = ref(false);
const selectedBarrierId = ref<string | null>(null);

function isoDate(offsetDays = 0): string {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  return date.toISOString().slice(0, 10);
}

const filter = reactive<AnalyticsFilter>({
  datasetId: '',
  from: isoDate(-29),
  to: isoDate(),
});

const facilityTypes = [
  ['ACCESSIBLE_ENTRANCE', '无障碍入口'],
  ['RAMP', '坡道'],
  ['ELEVATOR', '电梯'],
  ['ACCESSIBLE_TOILET', '无障碍卫生间'],
  ['REST_AREA', '休息点'],
  ['ACCESSIBLE_PARKING', '无障碍停车位'],
  ['DROP_OFF_POINT', '上下客点'],
  ['TRANSIT_BOARDING_POINT', '公交乘车点'],
] as const;

const barrierTypes = [
  ['STAIRS', '楼梯'],
  ['CONSTRUCTION', '施工'],
  ['TEMPORARY_CLOSURE', '临时封闭'],
  ['DAMAGED_SURFACE', '路面损坏'],
  ['NARROW_PATH', '道路狭窄'],
  ['VEHICLE_BLOCKING', '车辆占道'],
  ['STEEP_SLOPE', '陡坡'],
  ['ELEVATOR_OUTAGE', '电梯停运'],
  ['ENTRANCE_CLOSED', '入口关闭'],
  ['WATERLOGGING', '积水'],
] as const;

const selectedBarrier = computed(
  () => overview.value?.barrierPoints.find((item) => item.id === selectedBarrierId.value) ?? null,
);
const selectedBuilding = computed(
  () => overview.value?.buildingScores.find((item) => item.id === filter.buildingId) ?? null,
);
const selectedId = computed(() => selectedBarrierId.value ?? filter.buildingId ?? null);
const focusCoordinate = computed(() => {
  const target = selectedBarrier.value ?? selectedBuilding.value;
  return target ? { lng: target.lng, lat: target.lat } : null;
});
const heatPoints = computed(
  () =>
    overview.value?.barrierPoints.map((item) => ({
      lng: item.lng,
      lat: item.lat,
      count: item.impactWeight,
    })) ?? [],
);
const visibleBarrierIds = computed(
  () => overview.value?.barrierPoints.map((item) => item.id) ?? [],
);

const palette = computed<ChartPalette>(() =>
  theme.value === 'dark'
    ? {
        primary: '#36b8a4',
        secondary: '#5cb8ce',
        success: '#47cd89',
        warning: '#fdb022',
        danger: '#f97066',
        unknown: '#98a2b3',
        text: '#f1f7f5',
        muted: '#b9cac5',
        border: '#35504a',
      }
    : {
        primary: '#0f766e',
        secondary: '#176b82',
        success: '#067647',
        warning: '#b54708',
        danger: '#b42318',
        unknown: '#667085',
        text: '#18332d',
        muted: '#52645f',
        border: '#d5e2de',
      },
);

const buildingChart = computed(() =>
  buildingScoreOption(overview.value?.buildingScores ?? [], palette.value),
);
const facilityChart = computed(() =>
  facilityOption(overview.value?.facilityDistribution ?? [], palette.value),
);
const trendChart = computed(() => trendOption(overview.value?.barrierTrend ?? [], palette.value));
const routeChart = computed(() => routeRiskOption(overview.value?.routeRisks ?? [], palette.value));
const confidenceChart = computed(() =>
  confidenceOption(overview.value?.confidenceDistribution ?? [], palette.value),
);

async function refresh(): Promise<void> {
  if (!filter.datasetId) return;
  loading.value = true;
  governanceSummary.value = null;
  selectedBarrierId.value = null;
  try {
    overview.value = await analyticsApi.getOverview({ ...filter });
  } catch (reason: unknown) {
    ElMessage.error(readApiMessage(reason, '治理统计加载失败'));
  } finally {
    loading.value = false;
  }
}

async function selectDataset(datasetId: string): Promise<void> {
  filter.datasetId = datasetId;
  filter.buildingId = undefined;
  await mapData.select(datasetId, true);
  await refresh();
}

async function selectBuilding(id?: string): Promise<void> {
  filter.buildingId = id || undefined;
  await refresh();
}

function selectBuildingFromChart(index: number): void {
  const building = overview.value?.buildingScores[index];
  if (building) void selectBuilding(building.id);
}

function selectMapFeature(selection: { kind: string; id: string }): void {
  if (selection.kind === 'building') void selectBuilding(selection.id);
  if (selection.kind === 'barrier') selectedBarrierId.value = selection.id;
}

async function generateSummary(): Promise<void> {
  summaryLoading.value = true;
  try {
    governanceSummary.value = await analyticsApi.generateSummary({ ...filter });
  } catch (reason: unknown) {
    ElMessage.error(readApiMessage(reason, '治理建议生成失败'));
  } finally {
    summaryLoading.value = false;
  }
}

async function exportCsv(): Promise<void> {
  exporting.value = true;
  try {
    await analyticsApi.downloadCsv({ ...filter });
    ElMessage.success('已导出当前筛选统计');
  } catch (reason: unknown) {
    ElMessage.error(readApiMessage(reason, 'CSV 导出失败'));
  } finally {
    exporting.value = false;
  }
}

onMounted(async () => {
  await mapData.load(true);
  filter.datasetId = mapData.selectedDatasetId ?? '';
  await refresh();
});
</script>

<template>
  <section v-loading="loading" class="analytics-page" aria-labelledby="analytics-title">
    <header class="analytics-heading">
      <div>
        <p class="eyebrow">管理端 · 数据驱动治理</p>
        <h1 id="analytics-title">治理洞察</h1>
        <p>从真实路网、设施、障碍与路线历史中定位优先改造对象。</p>
      </div>
      <div class="analytics-actions">
        <el-button :loading="exporting" @click="exportCsv">导出当前 CSV</el-button>
        <el-button type="primary" :loading="summaryLoading" @click="generateSummary">
          生成治理建议
        </el-button>
      </div>
    </header>

    <form class="analytics-filters" aria-label="治理统计筛选" @submit.prevent="refresh">
      <label>
        <span>数据集</span>
        <el-select :model-value="filter.datasetId" @change="selectDataset">
          <el-option
            v-for="item in mapData.datasets"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </label>
      <label>
        <span>建筑</span>
        <el-select v-model="filter.buildingId" clearable placeholder="全部建筑">
          <el-option
            v-for="item in mapData.snapshot?.buildings ?? []"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </label>
      <label>
        <span>开始日期</span>
        <el-date-picker v-model="filter.from" type="date" value-format="YYYY-MM-DD" />
      </label>
      <label>
        <span>结束日期</span>
        <el-date-picker v-model="filter.to" type="date" value-format="YYYY-MM-DD" />
      </label>
      <label>
        <span>设施类型</span>
        <el-select v-model="filter.facilityType" clearable placeholder="全部设施">
          <el-option
            v-for="item in facilityTypes"
            :key="item[0]"
            :label="item[1]"
            :value="item[0]"
          />
        </el-select>
      </label>
      <label>
        <span>障碍类型</span>
        <el-select v-model="filter.barrierType" clearable placeholder="全部障碍">
          <el-option
            v-for="item in barrierTypes"
            :key="item[0]"
            :label="item[1]"
            :value="item[0]"
          />
        </el-select>
      </label>
      <label>
        <span>可信等级</span>
        <el-select v-model="filter.confidenceLevel" clearable placeholder="全部等级">
          <el-option label="HIGH · 实地核验" value="HIGH" />
          <el-option label="MEDIUM · 多源确认" value="MEDIUM" />
          <el-option label="LOW · 单一上报" value="LOW" />
          <el-option label="UNKNOWN · 未核验" value="UNKNOWN" />
        </el-select>
      </label>
      <el-button native-type="submit" type="primary">应用筛选</el-button>
    </form>

    <div v-if="overview" class="analytics-summary" aria-label="统计摘要">
      <div>
        <span>建筑</span><strong>{{ overview.summary.buildings }}</strong
        ><small>个</small>
      </div>
      <div>
        <span>平均评分</span><strong>{{ overview.summary.averageBuildingScore }}</strong
        ><small>分</small>
      </div>
      <div>
        <span>设施</span><strong>{{ overview.summary.facilities }}</strong
        ><small>项</small>
      </div>
      <div class="risk">
        <span>生效障碍</span><strong>{{ overview.summary.effectiveBarriers }}</strong
        ><small>项</small>
      </div>
      <div>
        <span>路线样本</span><strong>{{ overview.summary.routePlans }}</strong
        ><small>条</small>
      </div>
    </div>

    <section v-if="overview" class="analytics-map-section" aria-labelledby="spatial-title">
      <div class="analytics-map-heading">
        <div>
          <p class="eyebrow">空间分布</p>
          <h2 id="spatial-title">障碍地图与建筑评分</h2>
        </div>
        <el-button
          text
          :aria-expanded="!inspectorCollapsed"
          aria-controls="analytics-inspector"
          @click="inspectorCollapsed = !inspectorCollapsed"
          >{{ inspectorCollapsed ? '展开检查器' : '收起检查器' }}</el-button
        >
      </div>
      <div class="analytics-map-grid" :class="{ collapsed: inspectorCollapsed }">
        <CampusMap
          :snapshot="mapData.snapshot"
          :selected-id="selectedId"
          :heat-points="heatPoints"
          :focus-coordinate="focusCoordinate"
          :visible-barrier-ids="visibleBarrierIds"
          @feature-select="selectMapFeature"
        />
        <aside v-show="!inspectorCollapsed" id="analytics-inspector" class="analytics-inspector">
          <template v-if="selectedBarrier">
            <p class="eyebrow">障碍检查</p>
            <h3>{{ selectedBarrier.title }}</h3>
            <p>
              <span class="risk-label">已审核 · 影响 {{ selectedBarrier.impactWeight }}</span>
            </p>
            <dl>
              <dt>类型</dt>
              <dd>{{ selectedBarrier.barrierType }}</dd>
              <dt>可信度</dt>
              <dd>{{ selectedBarrier.confidenceLevel }}</dd>
            </dl>
          </template>
          <template v-else-if="selectedBuilding">
            <p class="eyebrow">建筑检查</p>
            <h3>{{ selectedBuilding.name }} · {{ selectedBuilding.score }} 分</h3>
            <p v-if="!selectedBuilding.dataSufficient" class="data-warning">
              数据不足，评分需谨慎使用。
            </p>
            <dl class="score-breakdown">
              <dt>无障碍入口</dt>
              <dd>{{ selectedBuilding.entranceScore }} / 20</dd>
              <dt>电梯</dt>
              <dd>{{ selectedBuilding.elevatorScore }} / 15</dd>
              <dt>无障碍卫生间</dt>
              <dd>{{ selectedBuilding.toiletScore }} / 15</dd>
              <dt>道路可达性</dt>
              <dd>{{ selectedBuilding.roadScore }} / 25</dd>
              <dt>障碍影响</dt>
              <dd>{{ selectedBuilding.barrierScore }} / 15</dd>
              <dt>数据完整度</dt>
              <dd>{{ selectedBuilding.completenessScore }} / 10</dd>
            </dl>
            <ul class="compact-list">
              <li v-for="reason in selectedBuilding.reasons" :key="reason">{{ reason }}</li>
            </ul>
          </template>
          <template v-else>
            <p class="eyebrow">数据检查器</p>
            <h3>从地图或排名选择对象</h3>
            <p class="muted">
              热力强度表示障碍影响权重；颜色之外，障碍标记和检查器会给出文字状态。
            </p>
          </template>
        </aside>
      </div>
      <div class="building-ranking">
        <div class="section-copy">
          <h3>建筑无障碍评分排名</h3>
          <p>满分 100；灰色条表示数据不足。点击图表或下方按钮可与地图联动。</p>
        </div>
        <EChartPanel
          :option="buildingChart"
          :height="Math.max(260, overview.buildingScores.length * 44)"
          description="建筑无障碍评分降序条形图"
          @item-select="selectBuildingFromChart"
        />
        <div class="ranking-buttons" aria-label="建筑评分数据表">
          <button
            v-for="item in overview.buildingScores"
            :key="item.id"
            type="button"
            :aria-pressed="filter.buildingId === item.id"
            @click="selectBuilding(item.id)"
          >
            <span>{{ item.name }}</span
            ><strong>{{ item.score }} 分</strong
            ><small>{{ item.dataSufficient ? '可评估' : '数据不足' }}</small>
          </button>
        </div>
      </div>
    </section>

    <section v-if="overview" class="analytics-details" aria-label="统计图表">
      <article>
        <div class="section-copy">
          <h2>设施类型分布</h2>
          <p>回答当前筛选范围的设施结构。</p>
        </div>
        <EChartPanel :option="facilityChart" description="设施类型数量条形图" />
        <p v-if="!overview.facilityDistribution.length" class="chart-empty">
          当前筛选条件下没有设施数据。
        </p>
        <details v-else class="chart-data-table">
          <summary>查看设施数据表</summary>
          <table>
            <thead>
              <tr>
                <th>类型</th>
                <th>数量</th>
                <th>占比</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in overview.facilityDistribution" :key="item.key">
                <td>{{ item.label }}</td>
                <td>{{ item.count }}</td>
                <td>{{ item.percentage }}%</td>
              </tr>
            </tbody>
          </table>
        </details>
      </article>
      <article>
        <div class="section-copy">
          <h2>障碍上报趋势</h2>
          <p>实线表示新增上报，虚线表示审核通过。</p>
        </div>
        <EChartPanel :option="trendChart" description="障碍新增与审核通过趋势折线图" />
        <details class="chart-data-table">
          <summary>查看障碍趋势数据表</summary>
          <table>
            <thead>
              <tr>
                <th>日期</th>
                <th>新增</th>
                <th>审核通过</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in overview.barrierTrend" :key="item.date">
                <td>{{ item.date }}</td>
                <td>{{ item.submitted }}</td>
                <td>{{ item.approved }}</td>
              </tr>
            </tbody>
          </table>
        </details>
      </article>
      <article>
        <div class="section-copy">
          <h2>路线风险对比</h2>
          <p>只聚合真实规划历史，不用模拟数值补齐。</p>
        </div>
        <EChartPanel
          v-if="overview.routeRisks.length"
          :option="routeChart"
          description="三类路线平均风险与警告对比图"
        />
        <p v-else class="chart-empty">当前时间范围没有路线历史，暂不输出对比。</p>
        <details v-if="overview.routeRisks.length" class="chart-data-table">
          <summary>查看路线风险数据表</summary>
          <table>
            <thead>
              <tr>
                <th>路线</th>
                <th>样本</th>
                <th>平均高风险边</th>
                <th>平均警告</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in overview.routeRisks" :key="item.profile">
                <td>{{ item.profile }}</td>
                <td>{{ item.sampleCount }}</td>
                <td>{{ item.averageHighRiskEdges }}</td>
                <td>{{ item.averageWarningCount }}</td>
              </tr>
            </tbody>
          </table>
        </details>
      </article>
      <article>
        <div class="section-copy">
          <h2>数据可信度</h2>
          <p>按对象类型展示四级可信度，UNKNOWN 需优先核验。</p>
        </div>
        <EChartPanel :option="confidenceChart" description="建筑入口道路设施障碍可信度堆叠条形图" />
        <details class="chart-data-table">
          <summary>查看可信度数据表</summary>
          <table>
            <thead>
              <tr>
                <th>对象</th>
                <th>HIGH</th>
                <th>MEDIUM</th>
                <th>LOW</th>
                <th>UNKNOWN</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in overview.confidenceDistribution" :key="item.entityType">
                <td>{{ item.entityLabel }}</td>
                <td>{{ item.high }}</td>
                <td>{{ item.medium }}</td>
                <td>{{ item.low }}</td>
                <td>{{ item.unknown }}</td>
              </tr>
            </tbody>
          </table>
        </details>
      </article>
    </section>

    <section v-if="governanceSummary" class="governance-summary" aria-live="polite">
      <div>
        <p class="eyebrow">
          {{ governanceSummary.generatedBy === 'MODEL' ? '智能治理建议' : '规则统计摘要' }}
        </p>
        <h2>优先改造线索</h2>
      </div>
      <span v-if="governanceSummary.degraded" class="risk-label">模型降级，已保留基础统计</span>
      <p class="summary-text">{{ governanceSummary.text }}</p>
      <small>模型只解释已计算指标，不自行编造数值；建议不会自动执行审核或修改。</small>
    </section>
  </section>
</template>
