import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { usageApi, type UsageResponse, type UsageHistoryResponse } from '../../api/usage';
import { licensesApi, type License } from '../../api/licenses';
import { alertsApi, type Alert } from '../../api/alerts';
import { reportsApi } from '../../api/reports';

export default function TenantDashboard() {
  const { tenantId } = useParams<{ tenantId: string }>();
  const [usage, setUsage] = useState<UsageResponse | null>(null);
  const [history, setHistory] = useState<UsageHistoryResponse | null>(null);
  const [licenses, setLicenses] = useState<License[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [topAgents, setTopAgents] = useState<any[]>([]);
  const [topLogSources, setTopLogSources] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [historyWindow, setHistoryWindow] = useState('1m');

  const fetchData = async () => {
    if (!tenantId) return;
    try {
      const [usageData, historyData, licensesData, alertsData, agentsData, logSourcesData] = await Promise.all([
        usageApi.getCurrent(tenantId),
        usageApi.getHistory(tenantId, historyWindow, 60),
        licensesApi.getByTenant(tenantId),
        alertsApi.getAll({ tenantId, status: 'OPEN' }),
        usageApi.getTopDimensions(tenantId, 'agent', historyWindow, 5),
        usageApi.getTopDimensions(tenantId, 'logsource', historyWindow, 5),
      ]);
      setUsage(usageData);
      setHistory(historyData);
      setLicenses(licensesData);
      setAlerts(alertsData);
      setTopAgents(agentsData);
      setTopLogSources(logSourcesData);
    } catch (err) {
      console.error('Failed to load tenant dashboard:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, [tenantId, historyWindow]);

  const handleExportCsv = async () => {
    if (!tenantId) return;
    const now = new Date();
    const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    await reportsApi.downloadUsageCsv(tenantId, month);
  };

  const activeLicense = licenses.find(l => l.status === 'ACTIVE');

  if (loading) {
    return (
      <>
        <div className="page-header"><h1>Tenant Dashboard</h1></div>
        <div className="page-body"><div className="loading"><div className="loading-spinner" />Loading...</div></div>
      </>
    );
  }

  // Downsample history for chart (show every 5th point to keep chart readable, unless it's 1d where we only have few points)
  const chartData = history?.dataPoints
    ? history.dataPoints.filter((_, i) => historyWindow === '1d' ? true : i % 5 === 0).map(dp => ({
        time: dp.timestamp.split('T')[1] || dp.timestamp,
        received: dp.received,
        accepted: dp.accepted,
        dropped: dp.dropped,
      }))
    : [];

  const usageColor = (usage?.usagePercent ?? 0) >= 100 ? 'danger'
    : (usage?.usagePercent ?? 0) >= 70 ? 'warning' : 'success';

  return (
    <>
      <div className="page-header">
        <h1>{usage?.tenantName || 'Tenant'} Dashboard</h1>
        <p>Real-time EPS monitoring and usage analytics</p>
      </div>
      <div className="page-body">
        {/* Stat Cards */}
        <div className="stat-cards">
          <div className="stat-card">
            <div className="stat-card-label">Current EPS</div>
            <div className={`stat-card-value ${usageColor}`}>{usage?.currentEps ?? 0}</div>
            <div className="stat-card-sub">events / second</div>
          </div>
          <div className="stat-card">
            <div className="stat-card-label">Quota</div>
            <div className="stat-card-value">{usage?.quota ?? 0}</div>
            <div className="stat-card-sub">EPS limit</div>
          </div>
          <div className="stat-card">
            <div className="stat-card-label">Usage</div>
            <div className={`stat-card-value ${usageColor}`}>{usage?.usagePercent ?? 0}%</div>
            <div className="stat-card-sub">
              <div className="progress-bar" style={{ marginTop: 8 }}>
                <div
                  className={`progress-bar-fill ${usageColor}`}
                  style={{ width: `${Math.min(usage?.usagePercent ?? 0, 100)}%` }}
                />
              </div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-card-label">Dropped Today</div>
            <div className="stat-card-value danger">{usage?.droppedToday ?? 0}</div>
            <div className="stat-card-sub">events dropped</div>
          </div>
        </div>

        {/* Chart */}
        <div className="card" style={{ marginBottom: 'var(--space-base)' }}>
          <div className="card-header">
            <h3>EPS History ({historyWindow})</h3>
            <div style={{ display: 'flex', gap: 'var(--space-sm)' }}>
              <select className="form-input" style={{ width: 'auto', padding: 'var(--space-xs) var(--space-sm)', fontSize: '0.875rem' }} value={historyWindow} onChange={e => setHistoryWindow(e.target.value)}>
                <option value="1m">1 Minute</option>
                <option value="5m">5 Minutes</option>
                <option value="15m">15 Minutes</option>
                <option value="1d">1 Day</option>
              </select>
              <button className="btn btn-secondary btn-sm" onClick={handleExportCsv}>📥 Export CSV</button>
            </div>
          </div>
          <div className="card-body">
            <div className="chart-container">
              {chartData.length > 0 ? (
                <ResponsiveContainer width="100%" height={300}>
                  <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                    <defs>
                      <linearGradient id="colorReceived" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="var(--color-chart-received)" stopOpacity={0.3}/>
                        <stop offset="95%" stopColor="var(--color-chart-received)" stopOpacity={0}/>
                      </linearGradient>
                      <linearGradient id="colorAccepted" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="var(--color-chart-accepted)" stopOpacity={0.3}/>
                        <stop offset="95%" stopColor="var(--color-chart-accepted)" stopOpacity={0}/>
                      </linearGradient>
                      <linearGradient id="colorDropped" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="var(--color-chart-dropped)" stopOpacity={0.3}/>
                        <stop offset="95%" stopColor="var(--color-chart-dropped)" stopOpacity={0}/>
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                    <XAxis dataKey="time" stroke="var(--color-text-muted)" fontSize={11} interval="preserveStartEnd" tickLine={false} axisLine={false} />
                    <YAxis stroke="var(--color-text-muted)" fontSize={11} tickLine={false} axisLine={false} />
                    <Tooltip
                      contentStyle={{
                        background: 'var(--color-bg-card)',
                        border: '1px solid var(--color-border)',
                        borderRadius: 'var(--radius-sm)',
                        color: 'var(--color-text-primary)',
                        fontSize: '12px',
                        boxShadow: 'var(--shadow-md)'
                      }}
                      itemStyle={{ padding: 0 }}
                    />
                    <Legend wrapperStyle={{ fontSize: '12px', paddingTop: '10px' }} iconType="circle" />
                    <Area type="monotone" dataKey="received" stroke="var(--color-chart-received)" fill="url(#colorReceived)" strokeWidth={2} dot={false} activeDot={{ r: 4, strokeWidth: 0 }} />
                    <Area type="monotone" dataKey="accepted" stroke="var(--color-chart-accepted)" fill="url(#colorAccepted)" strokeWidth={2} dot={false} activeDot={{ r: 4, strokeWidth: 0 }} />
                    <Area type="monotone" dataKey="dropped" stroke="var(--color-chart-dropped)" fill="url(#colorDropped)" strokeWidth={2} dot={false} activeDot={{ r: 4, strokeWidth: 0 }} />
                  </AreaChart>
                </ResponsiveContainer>
              ) : (
                <div className="empty-state"><p>No history data yet</p></div>
              )}
            </div>
          </div>
        </div>

        <div className="grid-2">
          {/* License Info */}
          <div className="card">
            <div className="card-header"><h3>License Info</h3></div>
            <div className="card-body">
              {activeLicense ? (
                <div className="data-table-wrapper">
                  <table className="data-table">
                    <tbody>
                      <tr><td style={{ color: 'var(--color-text-muted)', width: '120px' }}>EPS Quota</td><td><strong>{activeLicense.epsQuota.toLocaleString()}</strong></td></tr>
                      <tr><td style={{ color: 'var(--color-text-muted)' }}>Start Date</td><td>{activeLicense.startDate}</td></tr>
                      <tr><td style={{ color: 'var(--color-text-muted)' }}>End Date</td><td>{activeLicense.endDate}</td></tr>
                      <tr><td style={{ color: 'var(--color-text-muted)' }}>Status</td><td><span className="badge success">{activeLicense.status}</span></td></tr>
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="empty-state"><p>No active license</p></div>
              )}
            </div>
          </div>

          {/* Alerts */}
          <div className="card">
            <div className="card-header"><h3>Open Alerts ({alerts.length})</h3></div>
            <div className="card-body" style={{ padding: 0 }}>
              {alerts.length > 0 ? (
                alerts.map(alert => (
                  <div key={alert.alertId} className="alert-item">
                    <span className={`badge ${alert.severity === 'CRITICAL' ? 'danger' : alert.severity === 'WARNING' ? 'warning' : 'info'}`}>
                      {alert.severity}
                    </span>
                    <div className="alert-item-content">
                      <div className="alert-item-message">{alert.message}</div>
                      <div className="alert-item-meta">
                        <span>{new Date(alert.triggeredAt).toLocaleString()}</span>
                      </div>
                    </div>
                  </div>
                ))
              ) : (
                <div className="empty-state"><p>No open alerts ✅</p></div>
              )}
            </div>
          </div>
        </div>

        {/* Today's Stats */}
        <div className="card" style={{ marginTop: 'var(--space-base)' }}>
          <div className="card-header"><h3>Today's Statistics</h3></div>
          <div className="card-body">
            <div className="stat-cards" style={{ marginBottom: 0 }}>
              <div className="stat-card">
                <div className="stat-card-label">Received</div>
                <div className="stat-card-value">{usage?.receivedToday?.toLocaleString() ?? 0}</div>
              </div>
              <div className="stat-card">
                <div className="stat-card-label">Accepted</div>
                <div className="stat-card-value success">{usage?.acceptedToday?.toLocaleString() ?? 0}</div>
              </div>
              <div className="stat-card">
                <div className="stat-card-label">Dropped</div>
                <div className="stat-card-value danger">{usage?.droppedToday?.toLocaleString() ?? 0}</div>
              </div>
            </div>
          </div>
        </div>

        {/* Top Dimensions */}
        <div className="grid-2" style={{ marginTop: 'var(--space-base)' }}>
          <div className="card">
            <div className="card-header"><h3>Top Agents ({historyWindow})</h3></div>
            <div className="card-body" style={{ padding: 0 }}>
              {topAgents.length > 0 ? (
                <div className="data-table-wrapper">
                  <table className="data-table">
                    <thead>
                      <tr><th>Agent</th><th>EPS</th><th>Count</th></tr>
                    </thead>
                    <tbody>
                      {topAgents.map((agent, i) => (
                        <tr key={i}>
                          <td style={{ fontWeight: 600 }}>{agent.name}</td>
                          <td><span className="badge info">{agent.eps} EPS</span></td>
                          <td style={{ color: 'var(--color-text-muted)' }}>{agent.count.toLocaleString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="empty-state"><p>No agent data</p></div>
              )}
            </div>
          </div>
          
          <div className="card">
            <div className="card-header"><h3>Top Log Sources ({historyWindow})</h3></div>
            <div className="card-body" style={{ padding: 0 }}>
              {topLogSources.length > 0 ? (
                <div className="data-table-wrapper">
                  <table className="data-table">
                    <thead>
                      <tr><th>Log Source</th><th>EPS</th><th>Count</th></tr>
                    </thead>
                    <tbody>
                      {topLogSources.map((source, i) => (
                        <tr key={i}>
                          <td style={{ fontWeight: 600 }}>{source.name}</td>
                          <td><span className="badge info">{source.eps} EPS</span></td>
                          <td style={{ color: 'var(--color-text-muted)' }}>{source.count.toLocaleString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="empty-state"><p>No log source data</p></div>
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
