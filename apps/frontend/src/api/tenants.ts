import client from './client';

export interface Tenant {
  tenantId: string;
  name: string;
  notificationEmail?: string;
  webhookUrl?: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export const tenantsApi = {
  getAll: () => client.get('/api/v1/tenants').then(r => r.data.data as Tenant[]),
  get: (id: string) => client.get(`/api/v1/tenants/${id}`).then(r => r.data.data as Tenant),
  create: (name: string, notificationEmail?: string, webhookUrl?: string) => 
    client.post('/api/v1/tenants', { name, notificationEmail, webhookUrl }).then(r => r.data.data as Tenant),
  update: (id: string, name: string, notificationEmail?: string, webhookUrl?: string) => 
    client.put(`/api/v1/tenants/${id}`, { name, notificationEmail, webhookUrl }).then(r => r.data.data as Tenant),
  disable: (id: string) => client.delete(`/api/v1/tenants/${id}`).then(r => r.data.data as Tenant),
};
