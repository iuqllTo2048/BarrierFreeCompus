<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as mapApi from '../services/map-api';
import { readApiMessage } from '../services/http';
import type {
  GeoJsonEntityType,
  GeoJsonFeatureCollection,
  GeoJsonImportPreview,
} from '../types/map';

const props = defineProps<{
  datasetId: string;
  datasetName: string;
  demo: boolean;
}>();
const emit = defineEmits<{ applied: [] }>();

const dialogOpen = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);
const importFileName = ref('');
const importPayload = ref<GeoJsonFeatureCollection | null>(null);
const importPreview = ref<GeoJsonImportPreview | null>(null);
const importPolicy = ref<'KEEP_TARGET' | 'OVERWRITE'>('KEEP_TARGET');
const importing = ref(false);
const importTypes: Array<{ type: GeoJsonEntityType; label: string }> = [
  { type: 'BUILDING', label: '建筑' },
  { type: 'ENTRANCE', label: '入口' },
  { type: 'NODE', label: '道路节点' },
  { type: 'EDGE', label: '道路' },
  { type: 'FACILITY', label: '设施' },
  { type: 'BARRIER', label: '管理员障碍' },
];

function openFilePicker(): void {
  fileInput.value?.click();
}

async function handleFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  try {
    const payload = JSON.parse(await file.text()) as GeoJsonFeatureCollection;
    if (!payload.schemaVersion && props.demo) {
      const result = await mapApi.importGeoJson(props.datasetId, payload);
      emit('applied');
      ElMessage.success(
        `兼容导入完成：${result.nodes} 节点、${result.edges} 道路、${result.facilities} 设施`,
      );
      return;
    }
    importing.value = true;
    importPayload.value = payload;
    importFileName.value = file.name;
    importPolicy.value = 'KEEP_TARGET';
    importPreview.value = await mapApi.previewGeoJson(props.datasetId, payload);
    dialogOpen.value = true;
  } catch (reason: unknown) {
    ElMessage.error(readApiMessage(reason, 'GeoJSON 文件解析或预检失败'));
  } finally {
    importing.value = false;
    input.value = '';
  }
}

async function confirmImport(): Promise<void> {
  if (!importPayload.value || !importPreview.value) return;
  importing.value = true;
  try {
    const result = await mapApi.applyGeoJson(
      props.datasetId,
      importPayload.value,
      importPreview.value,
      importPolicy.value,
    );
    dialogOpen.value = false;
    emit('applied');
    ElMessage.success(
      `导入完成：新增 ${result.created}、更新 ${result.updated}、保留本地 ${result.keptLocal}；备份 ${result.backupId}`,
    );
  } catch (reason: unknown) {
    ElMessage.error(readApiMessage(reason, '导入失败；数据未写入，请重新预检'));
  } finally {
    importing.value = false;
  }
}

defineExpose({ openFilePicker });
</script>

<template>
  <input
    ref="fileInput"
    class="visually-hidden"
    type="file"
    accept=".json,.geojson,application/geo+json"
    @change="handleFile"
  />
  <el-dialog
    v-model="dialogOpen"
    class="geojson-import-dialog"
    title="GeoJSON 安全导入预览"
    width="min(720px, calc(100vw - 32px))"
    :close-on-click-modal="!importing"
    :close-on-press-escape="!importing"
  >
    <div v-if="importPreview" class="geojson-import-preview">
      <p class="import-target">
        <strong>{{ importFileName }}</strong>
        <span>→ {{ datasetName }}</span>
      </p>
      <p class="merge-safety-note">本次仅合并新增和同编号对象；文件中缺失的本地对象不会被删除。</p>

      <div class="import-summary" role="table" aria-label="GeoJSON 导入对象统计">
        <div class="import-summary__row import-summary__head" role="row">
          <span role="columnheader">对象</span><span role="columnheader">新增</span
          ><span role="columnheader">相同</span><span role="columnheader">内容不同</span>
        </div>
        <div v-for="item in importTypes" :key="item.type" class="import-summary__row" role="row">
          <strong role="cell">{{ item.label }}</strong>
          <span role="cell">{{ importPreview.summaries[item.type]?.creates ?? 0 }}</span>
          <span role="cell">{{ importPreview.summaries[item.type]?.unchanged ?? 0 }}</span>
          <span role="cell">{{ importPreview.summaries[item.type]?.conflicts ?? 0 }}</span>
        </div>
      </div>

      <el-alert
        v-if="importPreview.errors.length"
        title="文件不能导入"
        type="error"
        :closable="false"
        show-icon
      >
        <ul class="import-message-list">
          <li v-for="message in importPreview.errors" :key="message">{{ message }}</li>
        </ul>
      </el-alert>
      <el-alert
        v-else-if="importPreview.warnings.length"
        title="导入说明"
        type="warning"
        :closable="false"
        show-icon
      >
        <ul class="import-message-list">
          <li v-for="message in importPreview.warnings" :key="message">{{ message }}</li>
        </ul>
      </el-alert>

      <fieldset v-if="importPreview.conflictSamples.length" class="conflict-policy-fieldset">
        <legend>同编号且内容不同的对象</legend>
        <el-radio-group v-model="importPolicy">
          <el-radio value="KEEP_TARGET">保留本地对象（推荐）</el-radio>
          <el-radio value="OVERWRITE">使用文件内容覆盖</el-radio>
        </el-radio-group>
        <p>示例：{{ importPreview.conflictSamples.join('、') }}</p>
      </fieldset>
    </div>
    <template #footer>
      <el-button :disabled="importing" @click="dialogOpen = false">取消</el-button>
      <el-button
        type="primary"
        :loading="importing"
        :disabled="!importPreview || importPreview.errors.length > 0"
        @click="confirmImport"
      >
        确认合并导入
      </el-button>
    </template>
  </el-dialog>
</template>
