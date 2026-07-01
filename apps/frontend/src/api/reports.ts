import client from './client';

export const reportsApi = {
  downloadUsageCsv: async (tenantId: string, month: string): Promise<string> => {
    const response = await client.get('/api/v1/reports/usage/csv', {
      params: { tenantId, month },
      responseType: 'blob',
    });

    // Try to extract filename from Content-Disposition header
    const disposition = response.headers['content-disposition'] || '';
    const filenameMatch = disposition.match(/filename="?([^";\n]+)"?/);
    const filename = filenameMatch?.[1] || `usage_${tenantId}_${month}.csv`;

    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);

    return filename;
  },
};

