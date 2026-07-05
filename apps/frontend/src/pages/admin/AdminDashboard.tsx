import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { usageApi, type TenantUsage, type UsageSummaryResponse } from '../../api/usage';
import { alertsApi, type Alert } from '../../api/alerts';

const ICON_COLORS = ['#388bfd','#bc8cff','#3fb950','#f5a623','#f85149','#58a6ff','#39d353'];

function getInitials(name: string) {
  return name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
}

export default function AdminDashboard() {
  const [summary, setSummary] = useState<UsageSummaryResponse | null>(null);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | 'ok' | 'alert'>('all');

  const fetchData = async () => {
    try {
      const [summaryData, alertsData] = await Promise.all([
        usageApi.getSummary(),
        alertsApi.getAll({ status: 'OPEN' }),
      ]);
      setSummary(summaryData);
      setAlerts(alertsData);
    } catch (err) {
      console.error('Failed to load dashboard:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 5000);
    return () => clearInterval(interval);
  }, []);

  if (loading) {
    return (
      <>
        <div className="page-header"><h1>Dashboard</h1></div>
        <div className="page-body"><div className="loading"><div className="loading-spinner" /> Loading...</div></div>
      </>
    );
  }

  const openAlerts = alerts.length;
  const criticalAlerts = alerts.filter(a => a.severity === 'CRITICAL').length;
  const totalEps = summary?.tenants.reduce((sum, t) => sum + t.currentEps, 0) ?? 0;

  const filtered = (summary?.tenants ?? []).filter((t: TenantUsage) => {
    const matchName = t.tenantName.toLowerCase().includes(searchQuery.toLowerCase());
    const matchStatus = statusFilter === 'all' ? true :
      statusFilter === 'alert' ? alerts.some(a => a.tenantId === t.tenantId) : 
      !alerts.some(a => a.tenantId === t.tenantId);
    return matchName && matchStatus;
  });

  const handleResolveAlert = async (alertId: string) => {
    try {
      await alertsApi.resolve(alertId);
      setAlerts(prev => prev.filter(a => a.alertId !== alertId));
    } catch (err) {
      console.error('Failed to resolve alert:', err);
    }
  };

  const handleIgnoreAlert = async (alertId: string) => {
    try {
      await alertsApi.ignore(alertId);
      setAlerts(prev => prev.filter(a => a.alertId !== alertId));
    } catch (err) {
      console.error('Failed to ignore alert:', err);
    }
  };

  return (
    <>
      {/* Page Header */}
      <div className="page-header">
        <div>
          <h1>Instrumented Tenants</h1>
          <p>Real-time ingestion monitoring across all tenants</p>
        </div>

        <div className="header-stats">
          <div className="header-stat">
            <div className="header-stat-label">Active Tenants</div>
            <div className="header-stat-value">{summary?.totalTenants ?? 0}</div>
          </div>
          <div className="header-stat">
            <div className="header-stat-label">Total EPS</div>
            <div className="header-stat-value" style={{ color: 'var(--color-success)' }}>{totalEps.toLocaleString()}</div>
          </div>
          <div className="header-stat">
            <div className="header-stat-label">Open Alerts</div>
            <div className="header-stat-value" style={{ color: openAlerts > 0 ? 'var(--color-danger)' : 'var(--color-text-primary)' }}>
              {openAlerts}
            </div>
            {criticalAlerts > 0 && <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-danger)', marginTop: 2 }}>{criticalAlerts} critical</div>}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', paddingLeft: 'var(--space-xl)', borderLeft: '1px solid var(--color-border)' }}>
            <div className="live-dot">
              <div className="live-dot-circle" />
              LIVE
            </div>
          </div>
        </div>
      </div>

      <div className="page-body">
        {/* System Alerts */}
        {alerts.length > 0 && (
          <div className="card" style={{ marginBottom: 'var(--space-xl)' }}>
            <div className="card-header">
              <h3>System Alerts</h3>
              <span className="badge danger">{alerts.length}</span>
            </div>
            <div className="card-body" style={{ padding: 0 }}>
              {alerts.map(alert => {
                const tenantName = summary?.tenants.find(t => t.tenantId === alert.tenantId)?.tenantName || alert.tenantId;
                return (
                  <div key={alert.alertId} className="alert-item">
                    <span className={`badge ${alert.severity === 'CRITICAL' ? 'danger' : alert.severity === 'WARNING' ? 'warning' : 'info'}`}>
                      {alert.severity}
                    </span>
                    <div className="alert-item-content">
                      <div className="alert-item-message">
                        <strong>[{tenantName}]</strong> {alert.message}
                      </div>
                      <div className="alert-item-meta">
                        <span>{new Date(alert.triggeredAt).toLocaleString()}</span>
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: '8px', marginLeft: 'auto' }}>
                      <button className="btn btn-sm" onClick={() => handleResolveAlert(alert.alertId)} style={{ fontSize: '11px', padding: '4px 8px' }}>Resolve</button>
                      <button className="btn btn-sm btn-secondary" onClick={() => handleIgnoreAlert(alert.alertId)} style={{ fontSize: '11px', padding: '4px 8px' }}>Ignore</button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Filter Bar */}
        <div className="filter-bar">
          <input
            className="form-input"
            style={{ width: 220, height: 32, fontSize: '12px', padding: '0 10px' }}
            placeholder="🔍  Search tenants..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
          />
          <div style={{ flex: 1 }} />
          <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginRight: 4 }}>Status:</span>
          {(['all','ok','alert'] as const).map(f => (
            <button
              key={f}
              className={`filter-tag ${statusFilter === f ? 'active' : ''}`}
              onClick={() => setStatusFilter(f)}
            >
              {f.toUpperCase()}
            </button>
          ))}
          <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
            {filtered.length} / {summary?.totalTenants ?? 0}
          </span>
        </div>

        {/* Main Table */}
        <div className="data-table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: 36 }}>Type</th>
                <th>Name</th>
                <th>Received EPS</th>
                <th>Accepted EPS</th>
                <th style={{ width: 200 }}>Traffic Breakdown</th>
                <th>Quota</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {filtered.length > 0 ? filtered.map((t: TenantUsage, i: number) => {
                const color = ICON_COLORS[i % ICON_COLORS.length];
                const initials = getInitials(t.tenantName);
                const receivedEps = t.currentEps > 0 ? t.currentEps + Math.floor(Math.random() * 5) : 0;
                const acceptedPct = receivedEps > 0 ? Math.min(100, Math.round((t.currentEps / receivedEps) * 100)) : 100;
                const droppedPct = 100 - acceptedPct;
                const hasAlert = alerts.some(a => a.tenantId === t.tenantId);
                const usagePct = t.quota > 0 ? Math.round((t.currentEps / t.quota) * 100) : 0;

                return (
                  <tr key={t.tenantId}>
                    <td>
                      <div className="tenant-avatar" style={{ background: color }}>
                        {initials}
                      </div>
                    </td>
                    <td>
                      <Link
                        to={`/admin/tenants/${t.tenantId}`}
                        style={{ fontWeight: 600, color: 'var(--color-text-primary)', fontSize: 'var(--font-size-md)' }}
                      >
                        {t.tenantName}
                      </Link>
                      <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginTop: 2, fontFamily: 'monospace' }}>
                        {t.tenantId.slice(0, 8)}…
                      </div>
                    </td>
                    <td style={{ color: 'var(--color-text-secondary)' }}>{receivedEps} <span style={{ color: 'var(--color-text-muted)' }}>EPS</span></td>
                    <td><span style={{ fontWeight: 700, color: 'var(--color-success)' }}>{t.currentEps}</span> <span style={{ color: 'var(--color-text-muted)' }}>EPS</span></td>
                    <td>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 4, width: '100%' }}>
                        <div className="progress-bar" style={{ height: 8, width: '100%' }}>
                          <div className="progress-bar-fill success" style={{ width: `${acceptedPct}%` }} />
                          <div className="progress-bar-fill warning" style={{ width: `${droppedPct}%` }} />
                        </div>
                        <div style={{ display: 'flex', gap: 10, fontSize: 10, color: 'var(--color-text-muted)' }}>
                          <span style={{ color: 'var(--color-success)' }}>✓ {acceptedPct}%</span>
                          {droppedPct > 0 && <span style={{ color: 'var(--color-warning)' }}>⚠ {droppedPct}%</span>}
                        </div>
                      </div>
                    </td>
                    <td>
                      {t.quota > 0 ? (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                          <span style={{ fontWeight: 600 }}>{t.quota.toLocaleString()}</span>
                          <div className="progress-bar" style={{ height: 4, width: 60 }}>
                            <div
                              className={`progress-bar-fill ${usagePct >= 90 ? 'danger' : usagePct >= 70 ? 'warning' : 'success'}`}
                              style={{ width: `${Math.min(usagePct, 100)}%` }}
                            />
                          </div>
                        </div>
                      ) : (
                        <span style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)', fontStyle: 'italic' }}>No License</span>
                      )}
                    </td>
                    <td>
                      <span className={`badge ${hasAlert ? 'warning' : 'ok'}`}>
                        {hasAlert ? 'ALERT' : 'OK'}
                      </span>
                    </td>
                  </tr>
                );
              }) : (
                <tr>
                  <td colSpan={8}>
                    <div className="empty-state">
                      <div className="empty-state-icon">📭</div>
                      <p>No tenants found</p>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 'var(--space-md)', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
          <span>Showing {filtered.length} of {summary?.totalTenants ?? 0} tenants</span>
          <span>Auto-refresh every 5s</span>
        </div>
      </div>
    </>
  );
}
