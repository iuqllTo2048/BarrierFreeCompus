import { http } from './http';
import type { ApiResponse } from '../types/auth';
import type { RoutePlanRequest, RoutePlanResponse } from '../types/map';

export async function planRoutes(request: RoutePlanRequest): Promise<RoutePlanResponse> {
  const response = await http.post<ApiResponse<RoutePlanResponse>>('/routes/plan', request);
  return response.data.data;
}
