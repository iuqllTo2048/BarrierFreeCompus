<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import CampusMap from '../components/CampusMap.vue';
import * as agentApi from '../services/agent-api';
import * as businessApi from '../services/business-api';
import { readApiMessage } from '../services/http';
import { useMapDataStore } from '../stores/map-data';
import type {
  AssistantStatus,
  BarrierDraft,
  ConversationView,
  RouteComparison,
} from '../types/agent';
import type { MobilityMode, RoutePlanResponse } from '../types/map';

interface TimelineItem {
  name: string;
  status: 'running' | 'done';
  summary?: string;
}

const mapData = useMapDataStore();
const status = ref<AssistantStatus | null>(null);
const conversation = ref<ConversationView | null>(null);
const input = ref('');
const mobilityMode = ref<MobilityMode>('WHEELCHAIR');
const sending = ref(false);
const assistantText = ref('');
const routeResult = ref<RoutePlanResponse | null>(null);
const comparison = ref<RouteComparison | null>(null);
const draft = ref<BarrierDraft | null>(null);
const timeline = ref<TimelineItem[]>([]);
const routes = computed(() => routeResult.value?.routes ?? []);
const modeOptions: Array<{ value: MobilityMode; label: string }> = [
  { value: 'WHEELCHAIR', label: '轮椅' },
  { value: 'CRUTCH', label: '拐杖' },
  { value: 'TEMPORARY_INJURY', label: '临时受伤' },
  { value: 'CART_LUGGAGE', label: '推车或行李' },
  { value: 'WALKING', label: '步行' },
];
const toolLabels: Record<string, string> = {
  searchCampusPlace: '识别校园地点',
  calculateAccessibleRoutes: '计算无障碍路线',
  searchFacilitiesNearRoute: '核对沿途设施',
  searchActiveBarriers: '核对生效障碍',
  compareRoutes: '比较路线风险',
  createBarrierReportDraft: '生成上报草稿',
};

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : {};
}

function eventSummary(value: unknown): string {
  const data = asRecord(value);
  const summary = asRecord(data.summary);
  if (typeof summary.count === 'number') return `${summary.count} 项结果`;
  if (typeof summary.routeCount === 'number') return `${summary.routeCount} 条候选路线`;
  if (typeof summary.recommendedProfile === 'string')
    return profileLabel(summary.recommendedProfile);
  if (typeof summary.status === 'string') return summary.status;
  return '已完成';
}

function handleEvent(event: { name: string; data: unknown }): void {
  const data = asRecord(event.data);
  if (event.name === 'tool_start' && typeof data.name === 'string') {
    timeline.value.push({ name: data.name, status: 'running' });
  } else if (event.name === 'tool_result' && typeof data.name === 'string') {
    const item = [...timeline.value].reverse().find((entry) => entry.name === data.name);
    if (item) {
      item.status = 'done';
      item.summary = eventSummary(event.data);
    }
  } else if (event.name === 'delta' && typeof data.text === 'string') {
    assistantText.value += data.text;
  } else if (event.name === 'route_result') {
    routeResult.value = event.data as RoutePlanResponse;
  } else if (event.name === 'comparison') {
    comparison.value = event.data as RouteComparison;
  } else if (event.name === 'barrier_draft') {
    draft.value = event.data as BarrierDraft;
  } else if (event.name === 'error' && typeof data.message === 'string') {
    assistantText.value = data.message;
  }
}

async function send(example?: string): Promise<void> {
  const content = (example ?? input.value).trim();
  if (!content || !mapData.selectedDatasetId || sending.value) return;
  sending.value = true;
  assistantText.value = '';
  routeResult.value = null;
  comparison.value = null;
  draft.value = null;
  timeline.value = [];
  try {
    conversation.value ??= await agentApi.createConversation(content.slice(0, 36));
    await agentApi.streamMessage(
      conversation.value.id,
      { datasetId: mapData.selectedDatasetId, content, mobilityMode: mobilityMode.value },
      handleEvent,
    );
    input.value = '';
  } catch (error: unknown) {
    assistantText.value =
      error instanceof Error ? error.message : '智能服务暂时不可用，基础路线规划仍可使用';
  } finally {
    sending.value = false;
  }
}

async function submitDraft(): Promise<void> {
  if (!draft.value) return;
  try {
    await businessApi.reportBarrier(draft.value.payload);
    await agentApi.confirmDraft(draft.value.id);
    draft.value = { ...draft.value, status: 'CONFIRMED' };
    ElMessage.success('上报已提交，等待管理员审核后才会影响路线');
  } catch (error: unknown) {
    ElMessage.error(readApiMessage(error, '草稿提交失败'));
  }
}

function profileLabel(value: string | null): string {
  return (
    { SHORTEST: '最短路线', ACCESSIBLE: '无障碍优先路线', BALANCED: '综合路线' }[value ?? ''] ??
    '暂无推荐'
  );
}
function riskLabel(value: string): string {
  return { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', UNKNOWN: '风险未知' }[value] ?? value;
}

onMounted(async () => {
  await mapData.load();
  [status.value] = await Promise.all([agentApi.getAgentStatus()]);
  try {
    const profile = await businessApi.getProfile();
    mobilityMode.value = profile.defaultMobilityMode;
  } catch {
    // 默认行动模式仍可继续使用。
  }
});
</script>

<template>
  <section class="assistant-workspace" aria-labelledby="assistant-title">
    <div class="assistant-map">
      <CampusMap :snapshot="mapData.snapshot" :routes="routes" :selected-route-index="0" />
      <div class="assistant-heading">
        <p class="eyebrow">自然语言入口 · 真实 A* 路网</p>
        <h1 id="assistant-title">智能路线助手</h1>
        <p>AI 负责理解与解释，路线和风险始终来自后端白名单工具。</p>
      </div>
    </div>

    <aside class="assistant-panel" aria-label="智能路线咨询面板">
      <header class="assistant-panel-header">
        <div>
          <span class="mode-indicator" :class="status?.mode.toLowerCase()">
            {{ status?.mode === 'REAL' ? '真实模型' : '本地演示模式' }}
          </span>
          <h2>描述你的出行需求</h2>
        </div>
        <span class="provider-name">{{ status?.provider ?? '加载中' }}</span>
      </header>

      <label class="field-label" for="assistant-mobility">行动方式</label>
      <el-select id="assistant-mobility" v-model="mobilityMode">
        <el-option
          v-for="item in modeOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <label class="field-label" for="assistant-message">路线、设施或障碍需求</label>
      <el-input
        id="assistant-message"
        v-model="input"
        type="textarea"
        :rows="4"
        maxlength="2000"
        show-word-limit
        placeholder="例如：从图书馆到体育与健康中心，轮椅怎么走？"
        @keydown.ctrl.enter="send()"
      />
      <el-button type="primary" class="assistant-send" :loading="sending" @click="send()">
        {{ sending ? '正在核对校园数据' : '开始分析' }}
      </el-button>

      <div v-if="!assistantText && !sending" class="assistant-examples">
        <p>可以这样问</p>
        <button type="button" @click="send('从图书馆到体育与健康中心，轮椅怎么走？')">
          图书馆 → 体育与健康中心
        </button>
        <button type="button" @click="send('当前校园有哪些生效障碍？')">查询生效障碍</button>
        <button type="button" @click="send('上报图书馆附近道路积水')">生成障碍草稿</button>
      </div>

      <ol v-if="timeline.length" class="tool-timeline" aria-label="业务工具执行进度">
        <li v-for="(item, index) in timeline" :key="`${item.name}-${index}`" :class="item.status">
          <span aria-hidden="true">{{ item.status === 'done' ? '✓' : '…' }}</span>
          <div>
            <strong>{{ toolLabels[item.name] ?? item.name }}</strong
            ><small>{{ item.summary ?? '执行中' }}</small>
          </div>
        </li>
      </ol>

      <section v-if="assistantText" class="assistant-answer" aria-live="polite">
        <p class="eyebrow">路线助手结论</p>
        <p>{{ assistantText }}</p>
      </section>

      <section v-if="comparison" class="assistant-route-results">
        <div class="result-heading">
          <h3>路线对比</h3>
          <span>推荐：{{ profileLabel(comparison.recommendedProfile) }}</span>
        </div>
        <article
          v-for="route in comparison.routes"
          :key="route.profile"
          class="assistant-route-card"
        >
          <strong>{{ profileLabel(route.profile) }}</strong>
          <span>{{ Math.round(route.distanceM) }} 米 · 约 {{ route.estimatedMinutes }} 分钟</span>
          <span :class="`risk-text risk-${route.riskLevel.toLowerCase()}`">
            {{ riskLabel(route.riskLevel) }} ·
            {{ route.stairsCount ? `${route.stairsCount} 级楼梯` : '无楼梯' }}
          </span>
        </article>
      </section>

      <section v-if="draft" class="barrier-draft" aria-labelledby="draft-title">
        <div class="result-heading">
          <h3 id="draft-title">障碍上报草稿</h3>
          <span>{{ draft.status === 'CONFIRMED' ? '已提交' : '尚未生效' }}</span>
        </div>
        <dl>
          <div>
            <dt>标题</dt>
            <dd>{{ draft.payload.title }}</dd>
          </div>
          <div>
            <dt>类型</dt>
            <dd>{{ draft.payload.barrierType }}</dd>
          </div>
          <div>
            <dt>预计持续</dt>
            <dd>{{ draft.payload.expectedDurationHours }} 小时</dd>
          </div>
          <div>
            <dt>坐标</dt>
            <dd>{{ draft.payload.lng.toFixed(5) }}, {{ draft.payload.lat.toFixed(5) }}</dd>
          </div>
        </dl>
        <el-button v-if="draft.status === 'PENDING'" type="primary" @click="submitDraft">
          核对无误，正常提交
        </el-button>
        <p class="draft-note">提交后仍需管理员审核；AI 无权让障碍直接生效。</p>
      </section>
    </aside>
  </section>
</template>

<style scoped>
.assistant-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 420px);
  height: calc(100vh - 64px);
  min-height: 620px;
  overflow: hidden;
  background: var(--color-bg);
}
.assistant-map {
  position: relative;
  min-width: 0;
  min-height: 0;
  border-right: 1px solid var(--color-border);
}
.assistant-map :deep(.campus-map-shell) {
  height: 100%;
  min-height: 100%;
}
.assistant-heading {
  position: absolute;
  top: 24px;
  left: 24px;
  z-index: 3;
  width: min(520px, calc(100% - 48px));
  padding: 16px 20px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: color-mix(in srgb, var(--color-surface) 94%, transparent);
  box-shadow: 0 8px 24px rgb(15 46 40 / 12%);
}
.assistant-heading h1 {
  margin: 2px 0 4px;
  font-size: 24px;
}
.assistant-heading p {
  margin: 0;
  color: var(--color-text-secondary);
}
.assistant-panel {
  position: relative;
  z-index: 2;
  min-width: 0;
  padding: 24px;
  overflow-y: auto;
  background: var(--color-surface);
}
.assistant-panel-header,
.result-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.assistant-panel-header h2 {
  margin: 8px 0 16px;
  font-size: 20px;
}
.mode-indicator,
.provider-name,
.result-heading span {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 3px 8px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.mode-indicator::before {
  content: '';
  width: 7px;
  height: 7px;
  margin-right: 6px;
  border-radius: 50%;
  background: var(--color-success);
}
.mode-indicator.mock::before {
  background: var(--color-warning);
}
.assistant-panel .field-label {
  display: block;
  margin: 12px 0 6px;
}
.assistant-send {
  width: 100%;
  margin-top: 12px;
  min-height: 44px;
}
.assistant-examples {
  margin: 20px 0;
}
.assistant-examples p {
  margin: 0 0 8px;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.assistant-examples button {
  display: block;
  width: 100%;
  min-height: 38px;
  margin: 6px 0;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
}
.tool-timeline {
  margin: 18px 0;
  padding: 0;
  list-style: none;
  border-left: 2px solid var(--color-border);
}
.tool-timeline li {
  display: flex;
  gap: 10px;
  padding: 6px 0 8px 12px;
}
.tool-timeline li > span {
  color: var(--color-warning);
  font-weight: 700;
}
.tool-timeline li.done > span {
  color: var(--color-success);
}
.tool-timeline strong,
.tool-timeline small {
  display: block;
}
.tool-timeline small {
  margin-top: 2px;
  color: var(--color-text-secondary);
}
.assistant-answer,
.assistant-route-results,
.barrier-draft {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}
.assistant-answer > p:last-child {
  margin-bottom: 0;
  line-height: 1.7;
}
.result-heading h3 {
  margin: 0 0 10px;
  font-size: 16px;
}
.assistant-route-card {
  display: grid;
  gap: 4px;
  margin: 8px 0;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-muted);
}
.assistant-route-card span {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.risk-text {
  font-weight: 600;
}
.risk-high,
.risk-medium {
  color: var(--color-danger) !important;
}
.risk-unknown {
  color: var(--color-unknown) !important;
}
.risk-low {
  color: var(--color-success) !important;
}
.barrier-draft dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.barrier-draft dl div {
  padding: 8px;
  background: var(--color-surface-muted);
  border-radius: 8px;
}
.barrier-draft dt {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.barrier-draft dd {
  margin: 3px 0 0;
  overflow-wrap: anywhere;
}
.draft-note {
  margin-bottom: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
@media (max-width: 1023px) {
  .assistant-workspace {
    display: grid;
    grid-template-columns: 1fr;
    grid-template-rows: minmax(360px, 48vh) auto;
    height: auto;
    min-height: calc(100vh - 64px);
    overflow: visible;
  }
  .assistant-map {
    min-height: 360px;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }
  .assistant-heading {
    top: 16px;
    left: 16px;
    width: min(480px, calc(100% - 32px));
    padding: 12px 16px;
  }
  .assistant-panel {
    min-height: 0;
    padding: 24px;
    overflow: visible;
  }
  .assistant-map :deep(.campus-map-shell) {
    min-height: 360px;
  }
}
@media (max-width: 767px) {
  .assistant-workspace {
    grid-template-rows: minmax(300px, 42vh) auto;
    min-height: calc(100vh - 56px);
  }
  .assistant-map {
    min-height: 300px;
  }
  .assistant-heading {
    top: 12px;
    left: 12px;
    width: calc(100% - 24px);
    padding: 10px 12px;
  }
  .assistant-heading h1 {
    font-size: 20px;
  }
  .assistant-heading p {
    display: none;
  }
  .assistant-panel {
    padding: 16px;
  }
  .assistant-map :deep(.campus-map-shell) {
    min-height: 300px;
  }
}
@media (prefers-reduced-motion: reduce) {
  * {
    scroll-behavior: auto !important;
    transition: none !important;
  }
}
</style>
