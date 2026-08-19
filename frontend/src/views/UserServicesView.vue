<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute } from 'vue-router';
import CampusMap from '../components/CampusMap.vue';
import UiStatePanel from '../components/UiStatePanel.vue';
import * as businessApi from '../services/business-api';
import { readApiMessage } from '../services/http';
import { useMapDataStore } from '../stores/map-data';
import type {
  BarrierReport,
  FacilityDetail,
  RouteFavorite,
  RouteHistory,
  UserProfile,
} from '../types/business';
import type { Coordinate, MobilityMode } from '../types/map';

const mapData = useMapDataStore();
const route = useRoute();
const activeTab = ref('facility');
const busy = ref(false);
const myBarriers = ref<BarrierReport[]>([]);
const history = ref<RouteHistory[]>([]);
const favorites = ref<RouteFavorite[]>([]);
const facility = ref<FacilityDetail | null>(null);
const selectedFacilityId = ref('');
const commentText = ref('');
const suggestionText = ref('');
const suggestionType = ref('INFORMATION_CORRECTION');

const profile = reactive<UserProfile>({
  username: '',
  displayName: '',
  defaultMobilityMode: 'WALKING',
  avoidStairs: false,
  distanceWeight: 1,
  slopeWeight: 1,
  widthWeight: 1,
  preferRestArea: false,
  preferAccessibleToilet: false,
});
const report = reactive({
  title: '',
  barrierType: 'TEMPORARY_CLOSURE',
  description: '',
  expectedDurationHours: 24,
  lng: 112.9365,
  lat: 28.1775,
});

const mobilityOptions: Array<{ value: MobilityMode; label: string }> = [
  { value: 'WHEELCHAIR', label: '轮椅出行' },
  { value: 'CRUTCH', label: '拐杖辅助' },
  { value: 'TEMPORARY_INJURY', label: '临时受伤' },
  { value: 'CART_LUGGAGE', label: '推车 / 大件行李' },
  { value: 'WALKING', label: '普通步行' },
];
const barrierTypes = [
  ['STAIRS', '楼梯'],
  ['CONSTRUCTION', '施工'],
  ['TEMPORARY_CLOSURE', '临时封闭'],
  ['DAMAGED_SURFACE', '路面损坏'],
  ['NARROW_PATH', '道路狭窄'],
  ['VEHICLE_BLOCKING', '车辆阻挡'],
  ['STEEP_SLOPE', '陡坡'],
  ['ELEVATOR_OUTAGE', '电梯停用'],
  ['ENTRANCE_CLOSED', '入口关闭'],
  ['WATERLOGGING', '积水'],
];

function formatDate(value: string | null): string {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(
        new Date(value),
      )
    : '长期';
}
function statusLabel(value: string): string {
  return (
    { PENDING: '待审核', NEEDS_VERIFICATION: '待核验', APPROVED: '已通过', REJECTED: '已拒绝' }[
      value
    ] ?? value
  );
}
function pickCoordinate(value: Coordinate): void {
  report.lng = Number(value.lng.toFixed(6));
  report.lat = Number(value.lat.toFixed(6));
}

async function loadFacility(id = selectedFacilityId.value): Promise<void> {
  if (!id) return;
  busy.value = true;
  try {
    facility.value = await businessApi.getFacility(id);
    selectedFacilityId.value = id;
  } catch (error: unknown) {
    ElMessage.error(readApiMessage(error, '设施详情加载失败'));
  } finally {
    busy.value = false;
  }
}
async function rate(value: number): Promise<void> {
  if (!facility.value) return;
  await businessApi.rateFacility(facility.value.id, value);
  await loadFacility();
  ElMessage.success('评分已保存');
}
async function comment(): Promise<void> {
  if (!facility.value || !commentText.value.trim()) return;
  await businessApi.commentFacility(facility.value.id, commentText.value);
  commentText.value = '';
  await loadFacility();
  ElMessage.success('评论已发布');
}
async function suggest(): Promise<void> {
  if (!facility.value || !suggestionText.value.trim()) return;
  await businessApi.suggestFacility(facility.value.id, suggestionType.value, suggestionText.value);
  suggestionText.value = '';
  ElMessage.success('补充建议已提交审核');
}
async function submitReport(): Promise<void> {
  if (!mapData.snapshot) return;
  busy.value = true;
  try {
    await businessApi.reportBarrier({ ...report, datasetId: mapData.snapshot.dataset.id });
    Object.assign(report, { title: '', description: '' });
    await loadMyBarriers();
    activeTab.value = 'reports';
    ElMessage.success('障碍已提交，审核通过前不会影响路线');
  } catch (error: unknown) {
    ElMessage.error(readApiMessage(error, '障碍上报失败'));
  } finally {
    busy.value = false;
  }
}
async function loadMyBarriers(): Promise<void> {
  myBarriers.value = await businessApi.getMyBarriers();
}
async function loadHistory(): Promise<void> {
  history.value = await businessApi.getHistory();
}
async function loadFavorites(): Promise<void> {
  favorites.value = await businessApi.getFavorites();
}
async function addFavorite(item: RouteHistory): Promise<void> {
  const route = item.result.routes[0];
  if (!route) return;
  const answer = await ElMessageBox.prompt('为这条路线填写收藏名称', '收藏路线', {
    inputValue: `${item.startName} → ${item.endName}`,
  });
  await businessApi.favoriteHistory(item.id, route.profile, answer.value);
  await loadFavorites();
  ElMessage.success('路线已收藏');
}
async function removeHistory(id: string): Promise<void> {
  await businessApi.deleteHistory(id);
  await Promise.all([loadHistory(), loadFavorites()]);
}
async function removeFavorite(id: string): Promise<void> {
  await businessApi.deleteFavorite(id);
  await loadFavorites();
}
async function saveProfile(): Promise<void> {
  Object.assign(profile, await businessApi.updateProfile(profile));
  ElMessage.success('个人偏好已保存');
}

onMounted(async () => {
  await mapData.load(false);
  if (mapData.snapshot) {
    report.lng = mapData.snapshot.dataset.centerLng;
    report.lat = mapData.snapshot.dataset.centerLat;
    const queryFacility = typeof route.query.facility === 'string' ? route.query.facility : '';
    selectedFacilityId.value = queryFacility || mapData.snapshot.facilities[0]?.id || '';
  }
  const [profileData] = await Promise.all([
    businessApi.getProfile(),
    loadMyBarriers(),
    loadHistory(),
    loadFavorites(),
    loadFacility(),
  ]);
  Object.assign(profile, profileData);
});
</script>

<template>
  <section class="service-workspace" aria-labelledby="service-title">
    <header class="section-heading">
      <div>
        <p class="eyebrow">用户服务</p>
        <h1 id="service-title">出行记录与校园共治</h1>
        <p class="muted">查看设施信息、提交障碍、管理路线记录和个人出行偏好。</p>
      </div>
    </header>
    <el-tabs v-model="activeTab" class="service-tabs">
      <el-tab-pane label="设施详情" name="facility">
        <div class="facility-layout">
          <aside class="object-list-panel">
            <h2>无障碍设施</h2>
            <button
              v-for="item in mapData.snapshot?.facilities"
              :key="item.id"
              type="button"
              :class="{ selected: selectedFacilityId === item.id }"
              @click="loadFacility(item.id)"
            >
              <strong>{{ item.name }}</strong
              ><span>{{ item.facilityType }} · {{ item.openStatus }}</span>
            </button>
          </aside>
          <article v-if="facility" v-loading="busy" class="detail-panel">
            <div class="detail-heading">
              <div>
                <p class="eyebrow">{{ facility.facilityType }}</p>
                <h2>{{ facility.name }}</h2>
              </div>
              <span
                class="data-badge"
                :class="{ unknown: facility.confidenceLevel === 'UNKNOWN' }"
                >{{
                  facility.confidenceLevel === 'UNKNOWN' ? '未核验' : facility.confidenceLevel
                }}</span
              >
            </div>
            <p class="muted">{{ facility.description || '暂无设施说明' }}</p>
            <dl class="route-detail-grid">
              <div>
                <dt>开放状态</dt>
                <dd>{{ facility.openStatus }}</dd>
              </div>
              <div>
                <dt>所属建筑 / 楼层</dt>
                <dd>{{ facility.buildingName || '室外' }} · {{ facility.floorLabel || '未知' }}</dd>
              </div>
              <div>
                <dt>数据来源 / 可信度</dt>
                <dd>{{ facility.dataSource }} · {{ facility.confidenceLevel }}</dd>
              </div>
              <div>
                <dt>最近更新</dt>
                <dd>{{ formatDate(facility.updatedAt) }}</dd>
              </div>
              <div>
                <dt>平均评分</dt>
                <dd>{{ facility.averageRating }} / 5</dd>
              </div>
              <div>
                <dt>评分人数</dt>
                <dd>{{ facility.ratingCount }}</dd>
              </div>
            </dl>
            <label class="field-label">我的评分</label
            ><el-rate :model-value="facility.myRating ?? 0" @change="rate" />
            <section class="interaction-section">
              <h3>评论</h3>
              <div class="inline-form">
                <el-input
                  v-model="commentText"
                  maxlength="1000"
                  placeholder="分享实际通行体验"
                /><el-button type="primary" @click="comment">发布</el-button>
              </div>
              <ul class="comment-list">
                <li v-for="item in facility.comments" :key="item.id">
                  <strong>{{ item.username }}</strong
                  ><span>{{ formatDate(item.createdAt) }}</span>
                  <p>{{ item.content }}</p>
                </li>
              </ul>
            </section>
            <section class="interaction-section">
              <h3>补充建议</h3>
              <div class="two-column-fields">
                <el-select v-model="suggestionType"
                  ><el-option label="信息纠正" value="INFORMATION_CORRECTION" /><el-option
                    label="新增设施"
                    value="NEW_FACILITY" /><el-option
                    label="维护建议"
                    value="MAINTENANCE" /></el-select
                ><el-input
                  v-model="suggestionText"
                  maxlength="1000"
                  placeholder="说明需要补充或修正的内容"
                />
              </div>
              <el-button class="action-button" plain @click="suggest">提交建议</el-button>
            </section>
          </article>
        </div>
      </el-tab-pane>

      <el-tab-pane label="障碍上报" name="report">
        <div class="report-layout">
          <CampusMap :snapshot="mapData.snapshot" editable @map-click="pickCoordinate" />
          <aside class="report-form-panel">
            <h2>上报临时障碍</h2>
            <p class="interaction-hint">请先在地图点击障碍位置。审核通过前不会影响路线。</p>
            <label class="field-label" for="barrier-title">标题</label
            ><el-input id="barrier-title" v-model="report.title" maxlength="128" /><label
              class="field-label"
              for="barrier-type"
              >障碍类型</label
            ><el-select id="barrier-type" v-model="report.barrierType"
              ><el-option
                v-for="item in barrierTypes"
                :key="item[0]"
                :label="item[1]"
                :value="item[0]" /></el-select
            ><label class="field-label" for="barrier-description">具体描述</label
            ><el-input
              id="barrier-description"
              v-model="report.description"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
            /><label class="field-label" for="barrier-duration">预计持续时间（小时）</label
            ><el-input-number
              id="barrier-duration"
              v-model="report.expectedDurationHours"
              :min="1"
              :max="4320"
            />
            <div class="coordinate-grid">
              <div>
                <label class="field-label">经度</label
                ><el-input-number v-model="report.lng" :precision="6" :controls="false" />
              </div>
              <div>
                <label class="field-label">纬度</label
                ><el-input-number v-model="report.lat" :precision="6" :controls="false" />
              </div>
            </div>
            <el-button type="primary" class="plan-button" :loading="busy" @click="submitReport"
              >提交上报</el-button
            >
          </aside>
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的上报" name="reports"
        ><div class="record-list">
          <article v-for="item in myBarriers" :key="item.id" class="record-card">
            <div class="detail-heading">
              <div>
                <h2>{{ item.title }}</h2>
                <p>{{ item.barrierType }} · {{ formatDate(item.createdAt) }}</p>
              </div>
              <span class="status-chip"
                >{{ statusLabel(item.reviewStatus) }} · {{ item.confidenceLevel }}</span
              >
            </div>
            <p>{{ item.description }}</p>
            <small
              >预计结束：{{ formatDate(item.expiresAt) }}；{{
                item.active ? '已生效' : '尚未生效'
              }}</small
            >
          </article>
          <UiStatePanel
            v-if="!myBarriers.length"
            compact
            icon="warning"
            title="还没有障碍上报"
            description="发现影响通行的情况时，可在“障碍上报”中选择位置并提交。"
          /></div
      ></el-tab-pane>

      <el-tab-pane label="路线历史" name="history"
        ><div class="record-list">
          <article v-for="item in history" :key="item.id" class="record-card">
            <div class="detail-heading">
              <div>
                <h2>{{ item.startName }} → {{ item.endName }}</h2>
                <p>{{ item.mobilityMode }} · {{ formatDate(item.createdAt) }}</p>
              </div>
              <span class="data-badge">{{ item.result.routes.length }} 条候选</span>
            </div>
            <div class="record-actions">
              <el-button type="primary" plain @click="addFavorite(item)">收藏路线</el-button
              ><el-button text type="danger" @click="removeHistory(item.id)">删除历史</el-button>
            </div>
          </article>
          <UiStatePanel
            v-if="!history.length"
            compact
            title="还没有路线历史"
            description="完成一次路线规划后，路线和风险摘要会自动保存在这里。"
          /></div
      ></el-tab-pane>

      <el-tab-pane label="收藏路线" name="favorites"
        ><div class="record-list">
          <article v-for="item in favorites" :key="item.id" class="record-card">
            <div class="detail-heading">
              <div>
                <h2>{{ item.name }}</h2>
                <p>{{ item.routeProfile }} · {{ formatDate(item.createdAt) }}</p>
              </div>
              <el-button text type="danger" @click="removeFavorite(item.id)">取消收藏</el-button>
            </div>
          </article>
          <UiStatePanel
            v-if="!favorites.length"
            compact
            icon="services"
            title="还没有收藏路线"
            description="在路线结果中收藏常用路线，之后可以快速查看。"
          /></div
      ></el-tab-pane>

      <el-tab-pane label="个人中心" name="profile"
        ><article class="profile-panel">
          <h2>默认出行偏好</h2>
          <label class="field-label" for="display-name">显示名称</label
          ><el-input id="display-name" v-model="profile.displayName" maxlength="64" /><label
            class="field-label"
            for="default-mode"
            >默认行动方式</label
          ><el-select id="default-mode" v-model="profile.defaultMobilityMode"
            ><el-option
              v-for="item in mobilityOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
          /></el-select>
          <div class="preference-switch">
            <span>默认避开楼梯</span><el-switch v-model="profile.avoidStairs" />
          </div>
          <div class="preference-switch">
            <span>偏好休息点</span><el-switch v-model="profile.preferRestArea" />
          </div>
          <div class="preference-switch">
            <span>偏好无障碍卫生间</span><el-switch v-model="profile.preferAccessibleToilet" />
          </div>
          <label>距离权重 {{ profile.distanceWeight.toFixed(1) }}</label
          ><el-slider v-model="profile.distanceWeight" :min="0.5" :max="2" :step="0.1" /><label
            >坡度权重 {{ profile.slopeWeight.toFixed(1) }}</label
          ><el-slider v-model="profile.slopeWeight" :min="0.5" :max="2" :step="0.1" /><label
            >宽度权重 {{ profile.widthWeight.toFixed(1) }}</label
          ><el-slider v-model="profile.widthWeight" :min="0.5" :max="2" :step="0.1" /><el-button
            type="primary"
            class="plan-button"
            @click="saveProfile"
            >保存个人偏好</el-button
          >
        </article></el-tab-pane
      >
    </el-tabs>
  </section>
</template>
