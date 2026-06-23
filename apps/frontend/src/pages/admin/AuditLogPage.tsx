import { useEffect, useState } from 'react';
import { auditLogsApi, type AuditLog } from '../../api/auditLogs';

export default function AuditLogPage() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('');

  useEffect(() => {
    auditLogsApi.getAll()
      .then(setLogs)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const filtered = filter
    ? logs.filter(l => l.action.includes(filter) || l.resourceType.includes(filter))
    : logs;

  if (loading) {
    return (
      <>
        <div className="page-header"><h1>Audit Logs</h1></div>
        <div className="page-body"><div className="loading"><div className="loading-spinner" />Loading...</div></div>
      </>
    );
  }

  return (
    <>
      <div className="page-header">
        <h1>Audit Logs</h1>
        <p>Track all system changes</p>
      </div>
      <div className="page-body">
        <div style={{ marginBottom: 'var(--space-base)', display: 'flex', gap: 'var(--space-sm)' }}>
          <select className="form-select" style={{ width: 200 }} value={filter} onChange={e => setFilter(e.target.value)}>
            <option value="">All Actions</option>
            <option value="CREATE_TENANT">Create Tenant</option>
            <option value="UPDATE_TENANT">Update Tenant</option>
            <option value="DISABLE_TENANT">Disable Tenant</option>
            <option value="CREATE_LICENSE">Create License</option>
            <option value="UPDATE_LICENSE">Update License</option>
            <option value="DISABLE_LICENSE">Disable License</option>
            <option value="RESOLVE_ALERT">Resolve Alert</option>
            <option value="IGNORE_ALERT">Ignore Alert</option>
          </select>
        </div>

        <div className="card">
          <div className="card-body" style={{ padding: 0 }}>
            <div className="data-table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Actor</th>
                    <th>Action</th>
                    <th>Resource</th>
                    <th>Resource ID</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(log => (
                    <tr key={log.auditLogId}>
                      <td>{new Date(log.createdAt).toLocaleString()}</td>
                      <td><span className="badge info">{log.actor}</span></td>
                      <td>
                        <span className={`badge ${
                          log.action.startsWith('CREATE') ? 'success' :
                          log.action.startsWith('DISABLE') ? 'danger' :
                          log.action.startsWith('RESOLVE') ? 'success' :
                          'warning'
                        }`}>
                          {log.action}
                        </span>
                      </td>
                      <td>{log.resourceType}</td>
                      <td style={{ fontFamily: 'monospace', fontSize: 'var(--font-size-xs)' }}>
                        {log.resourceId ? log.resourceId.slice(0, 8) + '...' : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
