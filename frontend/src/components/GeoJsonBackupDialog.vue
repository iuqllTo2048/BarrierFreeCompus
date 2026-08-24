<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as mapApi from '../services/map-api';
import { readApiMessage } from '../services/http';
import type { GeoJsonBackup, GeoJsonEntityType, GeoJsonRestorePreview } from '../types/map';

const props = defineProps<{ datasetId: string }>();
const emit = defineEmits<{ applied: [] }>();

const dialogOpen = ref(false);
const backups = ref<GeoJsonBackup[]>([]);
const backupsLoading = ref(false);
const restorePreview = ref<GeoJsonRestorePreview | null>(null);
const restoring = ref(false);
const importTypes: Array<{ type: GeoJsonEntityType; label: string }> = [
  { type: 'BUILDING', label: '建筑' },
  { type: 'ENTRANCE', label: '入口' },
  { type: 'NODE', label: '道路节点' },
  { type: 'EDGE', label: '道路' },
  { type: 'FACILITY', label: '设施' },
  { type: 'BARRIER', label: '管理员障碍' },
];

async function open(): Promise<void> {
  dialogOpen.value = true;
  restorePreview.value = null;
  backupsLoading.value = true;
  try {
    backups.value = await mapApi.listGeoJsonBackups(props.datasetId);
    if (!backups.value.length) ElMessage.info('当前数据集还没有导入备份');
  } catch (reason: unknown) {
    ElMessage.error(readApiMessage(reason, '读取导入备份失败'));
  } finally {
    backupsLoading.value = false;
  }
}

async function previewRestoreBackup(backup: GeoJsonBackup): Promise<void> {
  try {
    restorePreview.value = await mapApi.previewGeoJsonRestore(props.datasetId, backup.id);
  } catch (reason: unknown) {
    ElMessage.error(readApiMessage(reason, '恢复预览失败，请刷新后重试'));
  }
}

async function confirmRestoreBackup(): Promise<void> {
  if (!restorePreview.value) return;
  restoring.value = true;
  try {
    const result = await mapApi.restoreGeoJsonBackup(
      props.datasetId,
      restorePreview.value.backupId,
      restorePreview.value.currentFingerprint,
    );
    dialogOpen.value = false;
    restorePreview.value = null;
    emit('applied');
    ElMessage.success(
      `已恢复：删除 ${Object.values(result.deleted).reduce((sum, n) => sum + n, 0)}、保留 ${Object.values(result.keptBusiness).reduce((sum, n) => sum + n, 0)}`,
    );
  } catch (reason: unknown) {
    ElMessage.error(readApiMessage(reason, '恢复失败；数据集可能已被修改，请重新预览'));
  } finally {
    restoring.value = false;
  }
}

function totalBackupObjects(backup: GeoJsonBackup): number {
  return Object.values(backup.objectCounts).reduce((sum, count) => sum + count, 0);
}

defineExpose({ open });
</script>

<template>
  <el-dialog
    v-model="dialogOpen"
    class="geojson-import-dialog"
    title="导入备份与一键恢复"
    width="min(720px, calc(100vw - 32px))"
    :close-on-click-modal="!restoring"
    :close-on-press-escape="!restoring"
  >
    <div v-if="backups.length" class="backup-list" role="list" aria-label="导入备份列表">
      <div v-for="backup in backups" :key="backup.id" class="backup-item">
        <div class="backup-item__info">
          <strong>{{ new Date(backup.createdAt).toLocaleString() }}</strong>
          <span>操作人：{{ backup.actorUsername ?? '系统' }}</span>
          <span>策略：{{ backup.conflictPolicy === 'KEEP_TARGET' ? '保留本地' : '覆盖' }}</span>
          <span>对象数：{{ totalBackupObjects(backup) }}</span>
        </div>
        <el-button size="small" @click="previewRestoreBackup(backup)">预览恢复</el-button>
      </div>
    </div>
    <el-empty v-else-if="!backupsLoading" description="当前数据集没有导入备份" />

    <div v-if="restorePreview" class="geojson-import-preview">
      <p class="merge-safety-note">
        恢复后该数据集的六类地图对象将替换为
        {{ new Date(restorePreview.backupCreatedAt).toLocaleString() }} 的快照；
        用户上报、评分、评论、建议和路线历史等业务数据不受影响。
      </p>
      <div class="import-summary" role="table" aria-label="恢复影响统计">
        <div class="import-summary__row import-summary__head" role="row">
          <span role="columnheader">对象</span><span role="columnheader">快照数量</span
          ><span role="columnheader">将删除</span><span role="columnheader">业务保留</span>
        </div>
        <div v-for="item in importTypes" :key="item.type" class="import-summary__row" role="row">
          <strong role="cell">{{ item.label }}</strong>
          <span role="cell">{{ restorePreview.snapshotCounts[item.type] ?? 0 }}</span>
          <span role="cell">{{ restorePreview.toDeleteCounts[item.type] ?? 0 }}</span>
          <span role="cell">{{ restorePreview.keptBusinessCounts[item.type] ?? 0 }}</span>
        </div>
      </div>
      <el-alert title="恢复说明" type="warning" :closable="false" show-icon>
        <ul class="import-message-list">
          <li v-for="message in restorePreview.warnings" :key="message">{{ message }}</li>
        </ul>
      </el-alert>
    </div>
    <template #footer>
      <el-button :disabled="restoring" @click="dialogOpen = false">关闭</el-button>
      <el-button
        type="primary"
        :loading="restoring"
        :disabled="!restorePreview"
        @click="confirmRestoreBackup"
      >
        确认恢复
      </el-button>
    </template>
  </el-dialog>
</template>
