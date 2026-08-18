import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import * as mapApi from '../services/map-api';
import { readApiMessage } from '../services/http';
import type { DatasetView, MapSnapshot } from '../types/map';

export const useMapDataStore = defineStore('map-data', () => {
  const datasets = ref<DatasetView[]>([]);
  const selectedDatasetId = ref<string | null>(null);
  const snapshot = ref<MapSnapshot | null>(null);
  const loading = ref(false);
  const error = ref('');
  const selectedDataset = computed(
    () => datasets.value.find((dataset) => dataset.id === selectedDatasetId.value) ?? null,
  );

  async function load(admin = false): Promise<void> {
    loading.value = true;
    error.value = '';
    try {
      datasets.value = await mapApi.listDatasets(admin);
      if (
        !selectedDatasetId.value ||
        !datasets.value.some((item) => item.id === selectedDatasetId.value)
      ) {
        selectedDatasetId.value = datasets.value[0]?.id ?? null;
      }
      await refresh(admin);
    } catch (reason: unknown) {
      error.value = readApiMessage(reason, '地图数据加载失败');
    } finally {
      loading.value = false;
    }
  }

  async function refresh(admin = false): Promise<void> {
    if (!selectedDatasetId.value) {
      snapshot.value = null;
      return;
    }
    snapshot.value = await mapApi.getSnapshot(selectedDatasetId.value, admin);
  }

  async function select(datasetId: string, admin = false): Promise<void> {
    selectedDatasetId.value = datasetId;
    await refresh(admin);
  }

  return {
    datasets,
    selectedDatasetId,
    selectedDataset,
    snapshot,
    loading,
    error,
    load,
    refresh,
    select,
  };
});
