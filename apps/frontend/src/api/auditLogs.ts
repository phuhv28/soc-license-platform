import client from './client';

export interface AuditLog {
  auditLogId: string;
  actor: string;
  action: string;
  resourceType: string;
  resourceId: string | null;
  beforeValue: Record<string, unknown> | null;
  afterValue: Record<string, unknown> | null;
  createdAt: string;
}

export const auditLogsApi = {
  getAll: () => client.get('/api/v1/audit-logs').then(r => r.data.data as AuditLog[]),
};
