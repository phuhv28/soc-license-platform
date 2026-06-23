import client from './client';

export interface License {
  licenseId: string;
  tenantId: string;
  epsQuota: number;
  startDate: string;
  endDate: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateLicenseRequest {
  tenantId: string;
  epsQuota: number;
  startDate: string;
  endDate: string;
}

export interface UpdateLicenseRequest {
  epsQuota: number;
  startDate: string;
  endDate: string;
  status: string;
}

export const licensesApi = {
  getAll: () => client.get('/api/v1/licenses').then(r => r.data.data as License[]),
  get: (id: string) => client.get(`/api/v1/licenses/${id}`).then(r => r.data.data as License),
  getByTenant: (tenantId: string) => client.get(`/api/v1/licenses/tenant/${tenantId}`).then(r => r.data.data as License[]),
  create: (req: CreateLicenseRequest) => client.post('/api/v1/licenses', req).then(r => r.data.data as License),
  update: (id: string, req: UpdateLicenseRequest) => client.put(`/api/v1/licenses/${id}`, req).then(r => r.data.data as License),
  disable: (id: string) => client.delete(`/api/v1/licenses/${id}`).then(r => r.data.data as License),
  getExpiringSoon: (days = 7) => client.get(`/api/v1/licenses/expiring-soon?days=${days}`).then(r => r.data.data as License[]),
};
