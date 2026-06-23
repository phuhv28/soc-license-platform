import client from './client';

export interface Alert {
  alertId: string;
  tenantId: string;
  tenantName: string;
  licenseId: string | null;
  alertType: string;
  severity: string;
  status: string;
  message: string;
  thresholdPercent: number | null;
  currentPercent: number | null;
  triggeredAt: string;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export const alertsApi = {
  getAll: (params?: { tenantId?: string; status?: string; alertType?: string }) =>
    client.get('/api/v1/alerts', { params }).then(r => r.data.data as Alert[]),
  get: (id: string) => client.get(`/api/v1/alerts/${id}`).then(r => r.data.data as Alert),
  resolve: (id: string) => client.put(`/api/v1/alerts/${id}/resolve`).then(r => r.data.data as Alert),
  ignore: (id: string) => client.put(`/api/v1/alerts/${id}/ignore`).then(r => r.data.data as Alert),
};
