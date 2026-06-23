import client from './client';

export interface Tenant {
  tenantId: string;
  name: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export const tenantsApi = {
  getAll: () => client.get('/api/v1/tenants').then(r => r.data.data as Tenant[]),
  get: (id: string) => client.get(`/api/v1/tenants/${id}`).then(r => r.data.data as Tenant),
  create: (name: string) => client.post('/api/v1/tenants', { name }).then(r => r.data.data as Tenant),
  update: (id: string, name: string) => client.put(`/api/v1/tenants/${id}`, { name }).then(r => r.data.data as Tenant),
  disable: (id: string) => client.delete(`/api/v1/tenants/${id}`).then(r => r.data.data as Tenant),
};
