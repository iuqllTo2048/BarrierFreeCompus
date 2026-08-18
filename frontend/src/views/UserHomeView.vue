<script setup lang="ts">
import { computed, onMounted } from 'vue';
import CampusMap from '../components/CampusMap.vue';
import { useMapDataStore } from '../stores/map-data';

const mapData = useMapDataStore();
const activeBarriers = computed(
  () => mapData.snapshot?.barriers.filter((item) => item.active) ?? [],
);
const verifiedLabel = (confidence: string): string =>
  confidence === 'UNKNOWN' ? '未核验' : confidence;

onMounted(() => mapData.load(false));
</script>

<template>
  <section class="map-layout user-map-layout" aria-labelledby="user-title">
    <aside class="route-panel data-summary-panel">
      <p class="eyebrow">用户端 · 地图数据</p>
      <h1 id="user-title">云麓校园无障碍地图</h1>
      <p class="muted">查看已启用数据集中的道路、设施与生效障碍。A* 路线规划将在 Stage 3 接入。</p>

      <label class="field-label" for="user-dataset">当前数据集</label>
      <el-select
        id="user-dataset"
        :model-value="mapData.selectedDatasetId"
        :disabled="mapData.loading"
        @change="(value: string) => mapData.select(value, false)"
      >
        <el-option
          v-for="dataset in mapData.datasets"
          :key="dataset.id"
          :label="dataset.name"
          :value="dataset.id"
        />
      </el-select>

      <p v-if="mapData.error" class="form-error" role="alert">{{ mapData.error }}</p>
      <template v-if="mapData.snapshot">
        <div class="dataset-meta">
          <span class="data-badge">{{ mapData.snapshot.dataset.coordinateSystem }}</span>
          <span class="data-badge unknown">Demo · 未核验</span>
        </div>
        <dl class="metric-grid" aria-label="地图数据概况">
          <div>
            <dt>道路</dt>
            <dd>{{ mapData.snapshot.edges.length }}</dd>
          </div>
          <div>
            <dt>设施</dt>
            <dd>{{ mapData.snapshot.facilities.length }}</dd>
          </div>
          <div>
            <dt>建筑</dt>
            <dd>{{ mapData.snapshot.buildings.length }}</dd>
          </div>
          <div>
            <dt>生效障碍</dt>
            <dd>{{ activeBarriers.length }}</dd>
          </div>
        </dl>

        <section class="summary-section" aria-labelledby="facility-title">
          <h2 id="facility-title">附近无障碍设施</h2>
          <ul class="plain-list">
            <li v-for="facility in mapData.snapshot.facilities.slice(0, 5)" :key="facility.id">
              <span
                ><strong>{{ facility.name }}</strong
                ><small>{{ facility.facilityType }}</small></span
              >
              <span class="status-text"
                >{{ facility.openStatus }} · {{ verifiedLabel(facility.confidenceLevel) }}</span
              >
            </li>
          </ul>
        </section>

        <section class="summary-section" aria-labelledby="barrier-title">
          <h2 id="barrier-title">当前障碍</h2>
          <p v-if="!activeBarriers.length" class="muted">当前没有生效障碍。</p>
          <ul v-else class="plain-list risk-list">
            <li v-for="barrier in activeBarriers" :key="barrier.id">
              <span
                ><strong>{{ barrier.title }}</strong
                ><small>{{ barrier.barrierType }}</small></span
              >
              <span class="risk-label">需注意</span>
            </li>
          </ul>
        </section>
      </template>
    </aside>

    <CampusMap :snapshot="mapData.snapshot" />
  </section>
</template>
