import { http } from './http';
import type { ApiResponse } from '../types/auth';
import type { AnalyticsFilter, AnalyticsOverview, GovernanceSummary } from '../types/analytics';

async function data<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  return (await promise).data.data;
}

function params(filter: AnalyticsFilter): Record<string, string> {
  return Object.fromEntries(
    Object.entries(filter).filter((entry): entry is [string, string] => Boolean(entry[1])),
  );
}

export const getOverview = (filter: AnalyticsFilter): Promise<AnalyticsOverview> =>
  data(http.get('/admin/analytics/overview', { params: params(filter) }));

export const generateSummary = (filter: AnalyticsFilter): Promise<GovernanceSummary> =>
  data(http.post('/admin/analytics/ai-summary', null, { params: params(filter) }));

export async function downloadCsv(filter: AnalyticsFilter): Promise<void> {
  const response = await http.get<Blob>('/admin/analytics/export.csv', {
    params: params(filter),
    responseType: 'blob',
  });
  const url = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = url;
  link.download = `无碍智行-治理统计-${filter.from ?? '开始'}-${filter.to ?? '当前'}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}
