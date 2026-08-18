import { http } from './http';
import type { ApiResponse } from '../types/auth';
import type {
  AdminOverview,
  AdminUser,
  AuditEntry,
  BarrierReport,
  FacilityDetail,
  FacilitySuggestion,
  RouteFavorite,
  RouteHistory,
  SystemSetting,
  UserProfile,
} from '../types/business';

async function data<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  return (await promise).data.data;
}

export const getProfile = (): Promise<UserProfile> => data(http.get('/business/profile'));
export const updateProfile = (request: UserProfile): Promise<UserProfile> =>
  data(http.put('/business/profile', request));
export const getFacility = (id: string): Promise<FacilityDetail> =>
  data(http.get(`/business/facilities/${id}`));
export const rateFacility = (id: string, rating: number): Promise<void> =>
  data(http.put(`/business/facilities/${id}/rating`, { rating }));
export const commentFacility = (id: string, content: string): Promise<unknown> =>
  data(http.post(`/business/facilities/${id}/comments`, { content }));
export const suggestFacility = (
  id: string,
  suggestionType: string,
  content: string,
): Promise<unknown> =>
  data(http.post(`/business/facilities/${id}/suggestions`, { suggestionType, content }));
export const reportBarrier = (request: Record<string, unknown>): Promise<BarrierReport> =>
  data(http.post('/business/barriers', request));
export const getMyBarriers = (): Promise<BarrierReport[]> =>
  data(http.get('/business/barriers/mine'));
export const getHistory = (): Promise<RouteHistory[]> => data(http.get('/business/history'));
export const deleteHistory = (id: string): Promise<void> =>
  data(http.delete(`/business/history/${id}`));
export const favoriteHistory = (id: string, routeProfile: string, name: string): Promise<unknown> =>
  data(http.post(`/business/history/${id}/favorites`, { routeProfile, name }));
export const getFavorites = (): Promise<RouteFavorite[]> => data(http.get('/business/favorites'));
export const deleteFavorite = (id: string): Promise<void> =>
  data(http.delete(`/business/favorites/${id}`));

export const getAdminOverview = (): Promise<AdminOverview> =>
  data(http.get('/admin/business/overview'));
export const getAdminBarriers = (status = 'ALL'): Promise<BarrierReport[]> =>
  data(http.get('/admin/business/barriers', { params: { status } }));
export const reviewBarrier = (
  id: string,
  decision: string,
  fieldVerified: boolean,
  note: string,
): Promise<BarrierReport> =>
  data(http.put(`/admin/business/barriers/${id}/review`, { decision, fieldVerified, note }));
export const getAdminSuggestions = (): Promise<FacilitySuggestion[]> =>
  data(http.get('/admin/business/suggestions'));
export const reviewSuggestion = (id: string, status: 'ACCEPTED' | 'REJECTED'): Promise<void> =>
  data(http.put(`/admin/business/suggestions/${id}`, { status }));
export const getAdminUsers = (): Promise<AdminUser[]> => data(http.get('/admin/business/users'));
export const setUserEnabled = (id: number, enabled: boolean): Promise<void> =>
  data(http.patch(`/admin/business/users/${id}`, { enabled }));
export const getAudits = (): Promise<AuditEntry[]> => data(http.get('/admin/business/audits'));
export const getSettings = (): Promise<SystemSetting[]> =>
  data(http.get('/admin/business/settings'));
export const updateSetting = (key: string, value: string): Promise<SystemSetting> =>
  data(http.put(`/admin/business/settings/${encodeURIComponent(key)}`, { value }));
export const resetDemo = (datasetId: string): Promise<void> =>
  data(http.post(`/admin/business/datasets/${datasetId}/reset-demo`));
