import client from './client';

export interface UsageResponse {
  tenantId: string;
  tenantName: string;
  currentEps: number;
  quota: number;
  usagePercent: number;
  acceptedToday: number;
  droppedToday: number;
  receivedToday: number;
}

export interface DataPoint {
  timestamp: string;
  received: number;
  accepted: number;
  dropped: number;
}

export interface UsageHistoryResponse {
  tenantId: string;
  tenantName: string;
  window: string;
  dataPoints: DataPoint[];
}

export interface TenantUsage {
  tenantId: string;
  tenantName: string;
  currentEps: number;
  quota: number;
  usagePercent: number;
}

export interface UsageSummaryResponse {
  totalTenants: number;
  tenants: TenantUsage[];
}

export interface UsageDimensionResponse {
  name: string;
  receivedCount: number;
  acceptedCount: number;
  droppedCount: number;
  receivedEps: number;
  acceptedEps: number;
  droppedEps: number;
}

export interface PagedDimensionResponse {
  items: UsageDimensionResponse[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export const usageApi = {
  getCurrent: (tenantId: string) =>
    client.get(`/api/v1/usage/${tenantId}/current`).then(r => r.data.data as UsageResponse),
  getHistory: (tenantId: string, window = '1m', limit = 60) =>
    client.get(`/api/v1/usage/${tenantId}/history?window=${window}&limit=${limit}`).then(r => r.data.data as UsageHistoryResponse),
  getSummary: () =>
    client.get('/api/v1/usage/summary').then(r => r.data.data as UsageSummaryResponse),
  getTopDimensions: (tenantId: string, dimension: string, window = '5m', limit = 500) =>
    client.get(`/api/v1/usage/${tenantId}/dimensions/top?dimension=${dimension}&window=${window}&limit=${limit}`).then(r => r.data.data as UsageDimensionResponse[]),
  getDimensions: (tenantId: string, dimension: string, window = '5m', page = 1, pageSize = 20) =>
    client.get(`/api/v1/usage/${tenantId}/dimensions?dimension=${dimension}&window=${window}&page=${page}&pageSize=${pageSize}`).then(r => r.data.data as PagedDimensionResponse),
};
