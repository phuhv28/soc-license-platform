import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { usageApi, type TenantUsage, type UsageSummaryResponse } from '../../api/usage';
import { alertsApi, type Alert } from '../../api/alerts';
import { licensesApi, type License } from '../../api/licenses';

export default function AdminDashboard() {
  const [summary, setSummary] = useState<UsageSummaryResponse | null>(null);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [expiringLicenses, setExpiringLicenses] = useState<License[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = async () => {
    try {
      const [summaryData, alertsData, licensesData] = await Promise.all([
        usageApi.getSummary(),
        alertsApi.getAll({ status: 'OPEN' }),
        licensesApi.getExpiringSoon(14),
      ]);
      setSummary(summaryData);
      setAlerts(alertsData);
      setExpiringLicenses(licensesData);
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

  const handleResolve = async (alertId: string) => {
    await alertsApi.resolve(alertId);
    fetchData();
  };

  const handleIgnore = async (alertId: string) => {
    await alertsApi.ignore(alertId);
    fetchData();
  };

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

  return (
    <>
      <div className="page-header">
        <h1>Admin Dashboard</h1>
        <p>System overview and monitoring</p>
      </div>
      <div className="page-body">
        {/* Stat Cards */}
        <div className="stat-cards">
          <div className="stat-card">
            <div className="stat-card-label">Active Tenants</div>
            <div className="stat-card-value">{summary?.totalTenants ?? 0}</div>
          </div>
          <div className="stat-card">
            <div className="stat-card-label">Expiring Licenses</div>
            <div className="stat-card-value warning">{expiringLicenses.length}</div>
            <div className="stat-card-sub">within 14 days</div>
          </div>
          <div className="stat-card">
            <div className="stat-card-label">Open Alerts</div>
            <div className="stat-card-value danger">{openAlerts}</div>
            <div className="stat-card-sub">{criticalAlerts} critical</div>
          </div>
        </div>

        <div className="grid-2">
          {/* Tenant Usage Table */}
          <div className="card">
            <div className="card-header">
              <h3>Tenant Usage</h3>
              <Link to="/admin/tenants" className="btn btn-secondary btn-sm">View All</Link>
            </div>
            <div className="card-body">
              {summary && summary.tenants.length > 0 ? (
                <div className="data-table-wrapper">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Tenant</th>
                        <th>EPS</th>
                        <th>Quota</th>
                        <th>Usage</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.tenants.map((t: TenantUsage) => (
                        <tr key={t.tenantId}>
                          <td>
                            <Link to={`/tenant/${t.tenantId}`}>{t.tenantName}</Link>
                          </td>
                          <td>{t.currentEps}</td>
                          <td>{t.quota}</td>
                          <td>
                            <div className="progress-bar-wrapper">
                              <div className="progress-bar">
                                <div
                                  className={`progress-bar-fill ${t.usagePercent >= 100 ? 'danger' : t.usagePercent >= 70 ? 'warning' : ''}`}
                                  style={{ width: `${Math.min(t.usagePercent, 100)}%` }}
                                />
                              </div>
                              <span className="progress-bar-label">{t.usagePercent}%</span>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="empty-state">
                  <div className="empty-state-icon">📊</div>
                  <p>No active tenants</p>
                </div>
              )}
            </div>
          </div>

          {/* Alerts & Expiring Licenses */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-base)' }}>
            {/* Open Alerts */}
            <div className="card">
              <div className="card-header">
                <h3>Open Alerts ({openAlerts})</h3>
              </div>
              <div className="card-body" style={{ padding: 0 }}>
                {alerts.length > 0 ? (
                  alerts.slice(0, 5).map(alert => (
                    <div key={alert.alertId} className="alert-item">
                      <span className={`badge ${alert.severity === 'CRITICAL' ? 'danger' : alert.severity === 'WARNING' ? 'warning' : 'info'}`}>
                        {alert.severity}
                      </span>
                      <div className="alert-item-content">
                        <div className="alert-item-message">{alert.message}</div>
                        <div className="alert-item-meta">
                          <span>{alert.tenantName}</span>
                          <span>{new Date(alert.triggeredAt).toLocaleString()}</span>
                        </div>
                      </div>
                      <div className="alert-item-actions">
                        <button className="btn btn-success btn-sm" onClick={() => handleResolve(alert.alertId)}>✓</button>
                        <button className="btn btn-secondary btn-sm" onClick={() => handleIgnore(alert.alertId)}>✕</button>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="empty-state"><p>No open alerts ✅</p></div>
                )}
              </div>
            </div>

            {/* Expiring Licenses */}
            <div className="card">
              <div className="card-header">
                <h3>Expiring Licenses</h3>
                <Link to="/admin/licenses" className="btn btn-secondary btn-sm">Manage</Link>
              </div>
              <div className="card-body" style={{ padding: 0 }}>
                {expiringLicenses.length > 0 ? (
                  <div className="data-table-wrapper">
                    <table className="data-table">
                      <thead>
                        <tr><th>Tenant</th><th>Quota</th><th>Expires</th></tr>
                      </thead>
                      <tbody>
                        {expiringLicenses.map(lic => (
                          <tr key={lic.licenseId}>
                            <td>{lic.tenantId.slice(0, 8)}...</td>
                            <td>{lic.epsQuota} EPS</td>
                            <td><span className="badge warning">{lic.endDate}</span></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div className="empty-state"><p>No licenses expiring soon ✅</p></div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
