import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useMapDataStore } from './map-data';
import * as mapApi from '../services/map-api';
import type { DatasetView, MapSnapshot } from '../types/map';

vi.mock('../services/map-api');

const datasets: DatasetView[] = [
  {
    id: 'demo',
    code: 'DEMO',
    name: '演示数据',
    datasetType: 'DEMO',
    coordinateSystem: 'GCJ02',
    enabled: true,
    demo: true,
    seed: 20260818,
    description: null,
    centerLng: 112.9,
    centerLat: 28.1,
  },
  {
    id: 'formal',
    code: 'FORMAL',
    name: '正式数据',
    datasetType: 'FORMAL',
    coordinateSystem: 'GCJ02',
    enabled: true,
    demo: false,
    seed: null,
    description: null,
    centerLng: 112.9,
    centerLat: 28.1,
  },
];

const snapshot = (dataset: DatasetView): MapSnapshot => ({
  dataset,
  buildings: [],
  entrances: [],
  nodes: [],
  edges: [],
  facilities: [],
  barriers: [],
});

describe('地图数据 store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
  });

  it('首次加载选择首个数据集并获取快照', async () => {
    vi.mocked(mapApi.listDatasets).mockResolvedValue(datasets);
    vi.mocked(mapApi.getSnapshot).mockResolvedValue(snapshot(datasets[0]));
    const store = useMapDataStore();

    await store.load();

    expect(store.selectedDatasetId).toBe('demo');
    expect(store.selectedDataset?.name).toBe('演示数据');
    expect(store.snapshot?.dataset.id).toBe('demo');
    expect(store.loading).toBe(false);
  });

  it('原选择失效时安全回退，显式选择会刷新对应快照', async () => {
    const store = useMapDataStore();
    store.selectedDatasetId = 'removed';
    vi.mocked(mapApi.listDatasets).mockResolvedValue(datasets);
    vi.mocked(mapApi.getSnapshot)
      .mockResolvedValueOnce(snapshot(datasets[0]))
      .mockResolvedValueOnce(snapshot(datasets[1]));

    await store.load(true);
    await store.select('formal', true);

    expect(mapApi.getSnapshot).toHaveBeenNthCalledWith(1, 'demo', true);
    expect(mapApi.getSnapshot).toHaveBeenNthCalledWith(2, 'formal', true);
    expect(store.snapshot?.dataset.id).toBe('formal');
  });

  it('加载失败保留中文错误且解除 loading', async () => {
    vi.mocked(mapApi.listDatasets).mockRejectedValue(new Error('network details'));
    const store = useMapDataStore();

    await store.load();

    expect(store.error).toBe('地图数据加载失败');
    expect(store.loading).toBe(false);
  });
});
