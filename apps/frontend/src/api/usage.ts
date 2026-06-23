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

export const usageApi = {
  getCurrent: (tenantId: string) =>
    client.get(`/api/v1/usage/${tenantId}/current`).then(r => r.data.data as UsageResponse),
  getHistory: (tenantId: string, hours = 24) =>
    client.get(`/api/v1/usage/${tenantId}/history?hours=${hours}`).then(r => r.data.data as UsageHistoryResponse),
  getSummary: () =>
    client.get('/api/v1/usage/summary').then(r => r.data.data as UsageSummaryResponse),
};
