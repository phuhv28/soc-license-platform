import client from './client';

export const reportsApi = {
  downloadUsageCsv: async (tenantId: string, month: string): Promise<void> => {
    const response = await client.get('/api/v1/reports/usage/csv', {
      params: { tenantId, month },
      responseType: 'blob',
    });

    const blob = new Blob([response.data], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `usage_${tenantId}_${month}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  },
};
