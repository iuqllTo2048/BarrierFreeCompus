<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import CampusMap from '../components/CampusMap.vue';
import * as businessApi from '../services/business-api';
import * as agentApi from '../services/agent-api';
import { readApiMessage } from '../services/http';
import { useMapDataStore } from '../stores/map-data';
import type {
  AdminOverview,
  AdminUser,
  AuditEntry,
  BarrierReport,
  FacilitySuggestion,
  SystemSetting,
} from '../types/business';
import type { InvocationLog } from '../types/agent';

const mapData = useMapDataStore();
const tab = ref('overview');
const overview = ref<AdminOverview | null>(null);
const barriers = ref<BarrierReport[]>([]);
const users = ref<AdminUser[]>([]);
const audits = ref<AuditEntry[]>([]);
const settings = ref<SystemSetting[]>([]);
const suggestions = ref<FacilitySuggestion[]>([]);
const invocations = ref<InvocationLog[]>([]);
const selectedBarrierId = ref('');
const reviewNote = ref('');
const fieldVerified = ref(false);
const busy = ref(false);
const selectedBarrier = computed(
  () => barriers.value.find((item) => item.id === selectedBarrierId.value) ?? null,
);
const countLabels: Record<string, string> = {
  users: '用户',
  activeBarriers: '生效障碍',
  pendingBarriers: '待审核/核验',
  facilities: '启用设施',
  routePlans: '路线规划',
  suggestions: '待处理建议',
};

function formatDate(value: string | null): string {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(
        new Date(value),
      )
    : '—';
}
function statusLabel(value: string): string {
  return (
    { PENDING: '待审核', NEEDS_VERIFICATION: '待核验', APPROVED: '已通过', REJECTED: '已拒绝' }[
      value
    ] ?? value
  );
}
async function loadAll(): Promise<void> {
  const values = await Promise.all([
    businessApi.getAdminOverview(),
    businessApi.getAdminBarriers(),
    businessApi.getAdminUsers(),
    businessApi.getAudits(),
    businessApi.getSettings(),
    businessApi.getAdminSuggestions(),
    agentApi.getInvocationLogs(),
    mapData.load(true),
  ]);
  [
    overview.value,
    barriers.value,
    users.value,
    audits.value,
    settings.value,
    suggestions.value,
    invocations.value,
  ] = values;
  selectedBarrierId.value ||= barriers.value[0]?.id ?? '';
}
async function review(decision: string): Promise<void> {
  if (!selectedBarrier.value) return;
  busy.value = true;
  try {
    await businessApi.reviewBarrier(
      selectedBarrier.value.id,
      decision,
      fieldVerified.value,
      reviewNote.value,
    );
    reviewNote.value = '';
    fieldVerified.value = false;
    await loadAll();
    ElMessage.success('审核结果已保存并立即影响路线');
  } catch (error: unknown) {
    ElMessage.error(readApiMessage(error, '审核失败'));
  } finally {
    busy.value = false;
  }
}
async function toggleUser(item: unknown): Promise<void> {
  const candidate = item as Partial<AdminUser>;
  if (typeof candidate.id !== 'number' || typeof candidate.enabled !== 'boolean') return;
  try {
    await businessApi.setUserEnabled(candidate.id, !candidate.enabled);
    await loadAll();
  } catch (error: unknown) {
    ElMessage.error(readApiMessage(error, '用户状态更新失败'));
  }
}
async function saveSetting(item: SystemSetting): Promise<void> {
  await businessApi.updateSetting(item.key, item.value);
  ElMessage.success('设置已保存');
}
async function handleSuggestion(
  item: FacilitySuggestion,
  status: 'ACCEPTED' | 'REJECTED',
): Promise<void> {
  await businessApi.reviewSuggestion(item.id, status);
  await loadAll();
  ElMessage.success('设施建议状态已更新');
}
async function resetDemo(): Promise<void> {
  if (!mapData.selectedDatasetId) return;
  await ElMessageBox.confirm(
    '仅清理当前 Demo 的业务数据并恢复种子对象状态，Formal 数据不会受影响。',
    '确认重置 Demo',
    { type: 'warning', confirmButtonText: '确认重置' },
  );
  await businessApi.resetDemo(mapData.selectedDatasetId);
  await loadAll();
  ElMessage.success('Demo 已安全重置');
}

onMounted(loadAll);
</script>

<template>
  <section class="governance-workspace" aria-labelledby="governance-title">
    <header class="section-heading">
      <div>
        <p class="eyebrow">管理端 · 数据治理</p>
        <h1 id="governance-title">校园无障碍治理工作台</h1>
        <p class="muted">
          审核上报、核验可信度并管理用户和运行设置。地图状态与下一次路线规划实时联动。
        </p>
      </div>
      <el-button type="danger" plain @click="resetDemo">安全重置 Demo</el-button>
    </header>
    <el-tabs v-model="tab" class="service-tabs">
      <el-tab-pane label="数据总览" name="overview">
        <dl v-if="overview" class="governance-metrics">
          <div v-for="(value, key) in overview.counts" :key="key">
            <dt>{{ countLabels[key] ?? key }}</dt>
            <dd>{{ value }}</dd>
          </div>
        </dl>
        <section class="governance-summary">
          <h2>优先处理</h2>
          <p class="muted">多人相近上报会进入“待核验”，但只有管理员审核通过后才会影响路线。</p>
          <div class="record-list">
            <button
              v-for="item in overview?.pendingBarriers"
              :key="item.id"
              type="button"
              class="record-card selectable"
              @click="
                tab = 'barriers';
                selectedBarrierId = item.id;
              "
            >
              <strong>{{ item.title }}</strong
              ><span>{{ statusLabel(item.reviewStatus) }} · {{ item.confidenceLevel }}</span>
            </button>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="障碍审核" name="barriers">
        <div class="governance-map-layout">
          <CampusMap
            :snapshot="mapData.snapshot"
            :selected-id="selectedBarrierId"
            @feature-select="
              (item) => {
                if (item.kind === 'barrier') selectedBarrierId = item.id;
              }
            "
          />
          <aside class="review-panel">
            <h2>上报队列</h2>
            <el-select v-model="selectedBarrierId" filterable
              ><el-option
                v-for="item in barriers"
                :key="item.id"
                :label="`${statusLabel(item.reviewStatus)} · ${item.title}`"
                :value="item.id" /></el-select
            ><template v-if="selectedBarrier"
              ><div class="detail-heading">
                <div>
                  <p class="eyebrow">{{ selectedBarrier.barrierType }}</p>
                  <h3>{{ selectedBarrier.title }}</h3>
                </div>
                <span class="status-chip">{{ statusLabel(selectedBarrier.reviewStatus) }}</span>
              </div>
              <p>{{ selectedBarrier.description }}</p>
              <dl class="route-detail-grid">
                <div>
                  <dt>上报用户</dt>
                  <dd>{{ selectedBarrier.reporterUsername || '管理员/Demo' }}</dd>
                </div>
                <div>
                  <dt>可信度</dt>
                  <dd>{{ selectedBarrier.confidenceLevel }}</dd>
                </div>
                <div>
                  <dt>关联上报</dt>
                  <dd>{{ selectedBarrier.matchedReportId ? '已匹配' : '单条' }}</dd>
                </div>
                <div>
                  <dt>预计结束</dt>
                  <dd>{{ formatDate(selectedBarrier.expiresAt) }}</dd>
                </div>
              </dl>
              <label class="field-label" for="review-note">审核说明</label
              ><el-input
                id="review-note"
                v-model="reviewNote"
                type="textarea"
                :rows="3"
                maxlength="500"
              />
              <div class="preference-switch">
                <span>已完成实地核验（授予 HIGH）</span><el-switch v-model="fieldVerified" />
              </div>
              <div class="review-actions">
                <el-button type="primary" :loading="busy" @click="review('APPROVED')"
                  >审核通过</el-button
                ><el-button :loading="busy" @click="review('NEEDS_VERIFICATION')"
                  >要求核验</el-button
                ><el-button type="danger" plain :loading="busy" @click="review('REJECTED')"
                  >拒绝</el-button
                >
              </div></template
            >
          </aside>
        </div>
      </el-tab-pane>

      <el-tab-pane label="用户管理" name="users"
        ><el-table :data="users" stripe
          ><el-table-column prop="username" label="用户名" /><el-table-column
            prop="role"
            label="角色"
            width="120"
          /><el-table-column label="状态" width="120"
            ><template #default="scope"
              ><el-tag :type="scope.row.enabled ? 'success' : 'info'">{{
                scope.row.enabled ? '启用' : '已禁用'
              }}</el-tag></template
            ></el-table-column
          ><el-table-column label="创建时间" width="180"
            ><template #default="scope">{{
              formatDate(scope.row.createdAt)
            }}</template></el-table-column
          ><el-table-column label="操作" width="130"
            ><template #default="scope"
              ><el-button
                text
                :type="scope.row.enabled ? 'danger' : 'primary'"
                @click="toggleUser(scope.row)"
                >{{ scope.row.enabled ? '禁用' : '启用' }}</el-button
              ></template
            ></el-table-column
          ></el-table
        ></el-tab-pane
      >

      <el-tab-pane label="设施建议" name="suggestions"
        ><div class="record-list">
          <article v-for="item in suggestions" :key="item.id" class="record-card">
            <div class="detail-heading">
              <div>
                <h2>{{ item.facilityName || '新增设施建议' }}</h2>
                <p>
                  {{ item.username || '匿名' }} · {{ item.suggestionType }} ·
                  {{ formatDate(item.createdAt) }}
                </p>
              </div>
              <span class="status-chip">{{ item.status }}</span>
            </div>
            <p>{{ item.content }}</p>
            <div v-if="item.status === 'PENDING'" class="record-actions">
              <el-button type="primary" plain @click="handleSuggestion(item, 'ACCEPTED')"
                >采纳</el-button
              ><el-button type="danger" text @click="handleSuggestion(item, 'REJECTED')"
                >拒绝</el-button
              >
            </div>
          </article>
        </div></el-tab-pane
      >

      <el-tab-pane label="审计日志" name="audits"
        ><el-table :data="audits" stripe
          ><el-table-column prop="actor" label="操作者" width="140" /><el-table-column
            prop="action"
            label="动作"
            min-width="190"
          /><el-table-column prop="targetType" label="对象" width="150" /><el-table-column
            prop="targetId"
            label="对象编号"
            min-width="220"
          /><el-table-column prop="detail" label="说明" min-width="180" /><el-table-column
            label="时间"
            width="180"
            ><template #default="scope">{{
              formatDate(scope.row.createdAt)
            }}</template></el-table-column
          ></el-table
        ></el-tab-pane
      >

      <el-tab-pane label="智能体调用日志" name="agent-logs">
        <div class="agent-log-notice">
          仅展示脱敏运行摘要、工具名称与延迟；不保存或展示 API Key、完整堆栈和模型隐藏思维链。
        </div>
        <el-table :data="invocations" stripe>
          <el-table-column prop="username" label="用户" width="130" />
          <el-table-column label="Provider / 模型" min-width="190">
            <template #default="scope">
              <strong>{{ scope.row.provider }}</strong
              ><br />
              <span class="muted">{{ scope.row.modelName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="110">
            <template #default="scope">
              <el-tag :type="scope.row.success ? 'success' : 'danger'">
                {{ scope.row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="100">
            <template #default="scope">{{ scope.row.latencyMs }} ms</template>
          </el-table-column>
          <el-table-column label="业务工具" min-width="230">
            <template #default="scope">
              <span v-if="!scope.row.tools.length" class="muted">未调用工具</span>
              <div v-for="tool in scope.row.tools" :key="tool.toolName" class="agent-tool-line">
                <span :class="tool.success ? 'tool-ok' : 'tool-error'" aria-hidden="true">
                  {{ tool.success ? '✓' : '!' }}
                </span>
                {{ tool.toolName }} · {{ tool.latencyMs }} ms
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="requestId" label="Request ID" min-width="230" />
          <el-table-column label="错误摘要" min-width="180">
            <template #default="scope">{{ scope.row.errorSummary || '—' }}</template>
          </el-table-column>
          <el-table-column label="时间" width="180">
            <template #default="scope">{{ formatDate(scope.row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="系统设置" name="settings"
        ><div class="settings-list">
          <article v-for="item in settings" :key="item.key" class="setting-row">
            <div>
              <strong>{{ item.key }}</strong>
              <p>{{ item.description }}</p>
            </div>
            <el-input v-model="item.value" /><el-button
              type="primary"
              plain
              @click="saveSetting(item)"
              >保存</el-button
            >
          </article>
        </div></el-tab-pane
      >
    </el-tabs>
  </section>
</template>
