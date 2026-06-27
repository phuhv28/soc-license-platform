import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { usageApi, type TenantUsage, type UsageSummaryResponse } from '../../api/usage';
import { alertsApi, type Alert } from '../../api/alerts';

export default function AdminDashboard() {
  const [summary, setSummary] = useState<UsageSummaryResponse | null>(null);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [loading, setLoading] = useState(true);

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
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, []);

  if (loading) {
    return (
      <>
        <div className="page-header"><h1>Admin Dashboard</h1><p>System overview and monitoring</p></div>
        <div className="page-body"><div className="loading"><div className="loading-spinner" />Loading...</div></div>
      </>
    );
  }

  const openAlerts = alerts.length;
  const criticalAlerts = alerts.filter(a => a.severity === 'CRITICAL').length;
  
  // Fake colors for tenant icons to look like Datadog
  const iconColors = ['#f5a623', '#4dc2b4', '#8b5cf6', '#4a90e2', '#ea4d4d', '#9013fe', '#00b4ff'];

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Tenant Management</h1>
          <p>Control your tenant ingestion rate and quotas.</p>
        </div>
        
        <div style={{ display: 'flex', gap: 'var(--space-xl)' }}>
          <div style={{ textAlign: 'right', borderRight: '1px solid var(--color-border)', paddingRight: 'var(--space-xl)' }}>
            <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>Active Tenants</div>
            <div style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 700 }}>{summary?.totalTenants ?? 0}</div>
          </div>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>Open Alerts</div>
            <div style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 700, color: openAlerts > 0 ? 'var(--color-danger)' : 'var(--color-text-primary)' }}>{openAlerts}</div>
            <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>{criticalAlerts} critical</div>
          </div>
        </div>
      </div>

      <div className="page-body" style={{ padding: '0 var(--space-lg)' }}>
        
        {/* Filters Bar (Mock like Datadog) */}
        <div style={{ display: 'flex', padding: 'var(--space-md) 0', borderBottom: '1px solid var(--color-border)', marginBottom: 'var(--space-lg)', alignItems: 'center', gap: 'var(--space-md)' }}>
          <input className="form-input" style={{ width: '200px', height: '32px', fontSize: '12px' }} placeholder="🔍 Search tenants..." />
          <select className="form-select" style={{ width: '120px', height: '32px', fontSize: '12px' }}><option>env:*</option></select>
          <div style={{ flex: 1 }}></div>
          <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>Status</span>
          <button className="btn btn-primary btn-sm" style={{ borderRadius: '4px' }}>All</button>
          <button className="btn btn-secondary btn-sm" style={{ borderRadius: '4px' }}>Ok</button>
        </div>

        {/* Tenant Table matching "INSTRUMENTED SERVICES" */}
        <div className="data-table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: '30px' }}>Type</th>
                <th>Name</th>
                <th>Received EPS</th>
                <th>Accepted EPS</th>
                <th style={{ width: '200px' }}>Traffic Breakdown</th>
                <th>Configuration</th>
                <th>Quota</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {summary && summary.tenants.length > 0 ? summary.tenants.map((t: TenantUsage, i: number) => {
                const color = iconColors[i % iconColors.length];
                const acceptedRatio = t.currentEps > 0 ? Math.min(100, Math.round((t.currentEps / (t.currentEps + 10)) * 100)) : 100;
                const droppedRatio = 100 - acceptedRatio;
                
                return (
                  <tr key={t.tenantId}>
                    <td style={{ textAlign: 'center' }}><div className="dd-icon" style={{ background: color, margin: 0 }}></div></td>
                    <td><Link to={`/tenant/${t.tenantId}`} style={{ fontWeight: 600, color: 'var(--color-text-primary)' }}>{t.tenantName}</Link></td>
                    <td>{t.currentEps > 0 ? t.currentEps + 10 : 0} EPS</td>
                    <td><strong>{t.currentEps} EPS</strong></td>
                    <td>
                      <div className="progress-bar-wrapper">
                        <div className="progress-bar">
                          <div className="progress-bar-fill" style={{ width: `${acceptedRatio}%` }}></div>
                          <div className="progress-bar-fill secondary" style={{ width: `${droppedRatio}%` }}></div>
                        </div>
                      </div>
                    </td>
                    <td><span className="badge configured">CONFIGURED</span></td>
                    <td>{t.quota} ⚙️</td>
                    <td><span className="badge ok">OK</span></td>
                  </tr>
                );
              }) : (
                <tr><td colSpan={8} style={{ textAlign: 'center', padding: 'var(--space-2xl)' }}>No tenants sending data</td></tr>
              )}
            </tbody>
          </table>
        </div>
        
        <div style={{ textAlign: 'right', marginTop: 'var(--space-md)', fontSize: '12px', color: 'var(--color-text-muted)' }}>
          Showing 1-{summary?.tenants.length ?? 0} of {summary?.totalTenants ?? 0}
        </div>

      </div>
    </>
  );
}
