import { http } from './http';
import type { ApiResponse } from '../types/auth';
import type {
  DatasetView,
  EdgeRequest,
  GeoJsonFeatureCollection,
  MapSnapshot,
  NodeRequest,
  PointCreateRequest,
} from '../types/map';

export async function listDatasets(admin = false): Promise<DatasetView[]> {
  const prefix = admin ? '/admin/map' : '/map';
  const response = await http.get<ApiResponse<DatasetView[]>>(`${prefix}/datasets`);
  return response.data.data;
}

export async function getSnapshot(datasetId: string, admin = false): Promise<MapSnapshot> {
  const prefix = admin ? '/admin/map' : '/map';
  const response = await http.get<ApiResponse<MapSnapshot>>(
    `${prefix}/datasets/${datasetId}/snapshot`,
  );
  return response.data.data;
}

export async function setDatasetEnabled(datasetId: string, enabled: boolean): Promise<DatasetView> {
  const response = await http.patch<ApiResponse<DatasetView>>(`/admin/map/datasets/${datasetId}`, {
    enabled,
  });
  return response.data.data;
}

export async function saveNode(
  datasetId: string,
  request: NodeRequest,
  id?: string,
): Promise<string> {
  const path = `/admin/map/datasets/${datasetId}/nodes${id ? `/${id}` : ''}`;
  const response = id
    ? await http.put<ApiResponse<{ id: string }>>(path, request)
    : await http.post<ApiResponse<{ id: string }>>(path, request);
  return response.data.data.id;
}

export async function saveEdge(
  datasetId: string,
  request: EdgeRequest,
  id?: string,
): Promise<string> {
  const path = `/admin/map/datasets/${datasetId}/edges${id ? `/${id}` : ''}`;
  const response = id
    ? await http.put<ApiResponse<{ id: string }>>(path, request)
    : await http.post<ApiResponse<{ id: string }>>(path, request);
  return response.data.data.id;
}

export async function createMapObject(
  datasetId: string,
  type: 'buildings' | 'entrances' | 'facilities' | 'barriers',
  request: PointCreateRequest,
): Promise<string> {
  const response = await http.post<ApiResponse<{ id: string }>>(
    `/admin/map/datasets/${datasetId}/${type}`,
    request,
  );
  return response.data.data.id;
}

export async function exportGeoJson(datasetId: string): Promise<GeoJsonFeatureCollection> {
  const response = await http.get<ApiResponse<GeoJsonFeatureCollection>>(
    `/admin/map/datasets/${datasetId}/geojson`,
  );
  return response.data.data;
}

export async function importGeoJson(
  datasetId: string,
  payload: GeoJsonFeatureCollection,
): Promise<{ nodes: number; edges: number; facilities: number }> {
  const response = await http.post<
    ApiResponse<{ nodes: number; edges: number; facilities: number }>
  >(`/admin/map/datasets/${datasetId}/geojson`, payload);
  return response.data.data;
}
