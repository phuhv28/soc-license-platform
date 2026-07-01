import { useEffect, useState, useMemo, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Legend
} from 'recharts';
import {
  usageApi,
  type UsageResponse,
  type UsageHistoryResponse,
  type UsageDimensionResponse,
  type PagedDimensionResponse,
} from '../../api/usage';
import { licensesApi, type License } from '../../api/licenses';
import { alertsApi, type Alert } from '../../api/alerts';
import { reportsApi } from '../../api/reports';
import { useAuth } from '../../context/AuthContext';


const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const SOURCE_COLORS = ['#388bfd','#bc8cff','#3fb950','#f5a623','#f85149','#58a6ff','#39d353','#fb923c','#a78bfa','#34d399'];
const AGENT_COLORS  = ['#3fb950','#bc8cff','#388bfd','#f5a623','#f85149','#39d353','#58a6ff','#fb923c','#34d399','#a78bfa'];

function CustomTooltip({ active, payload, label }: any) {
  if (!active || !payload?.length) return null;
  return (
    <div style={{
      background: 'var(--color-bg-elevated)',
      border: '1px solid var(--color-border)',
      borderRadius: 'var(--radius-md)',
      padding: '10px 14px',
      boxShadow: 'var(--shadow-lg)',
      fontSize: 12,
    }}>
      <div style={{ color: 'var(--color-text-muted)', marginBottom: 6, fontSize: 11 }}>{label}</div>
      {payload.map((entry: any) => (
        <div key={entry.dataKey} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 3 }}>
          <div style={{ width: 8, height: 8, borderRadius: 2, background: entry.color, flexShrink: 0 }} />
          <span style={{ color: 'var(--color-text-secondary)', textTransform: 'capitalize' }}>{entry.dataKey}:</span>
          <span style={{ color: 'var(--color-text-primary)', fontWeight: 600 }}>{entry.value?.toLocaleString()}</span>
        </div>
      ))}
    </div>
  );
}

// ── Server-side Paged Dimension Table ────────────────────────────────

interface DimensionTableProps {
  title: string;
  tenantId: string;
  dimension: string;
  window: string;
  colors: string[];
}

function DimensionTable({ title, tenantId, dimension, window: win, colors }: DimensionTableProps) {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState('');
  const [data, setData] = useState<PagedDimensionResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [sortKey, setSortKey] = useState<keyof UsageDimensionResponse>('receivedEps');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');

  // Fetch from server whenever page/pageSize/window changes
  const fetchPage = useCallback(async () => {
    setLoading(true);
    try {
      const result = await usageApi.getDimensions(tenantId, dimension, win, page, pageSize);
      setData(result);
    } catch {
      // Silently ignore on interval refresh errors
    } finally {
      setLoading(false);
    }
  }, [tenantId, dimension, win, page, pageSize]);

  useEffect(() => {
    fetchPage();
    const interval = setInterval(fetchPage, 5000);
    return () => clearInterval(interval);
  }, [fetchPage]);

  // Reset to page 1 when window changes
  useEffect(() => { setPage(1); }, [win]);

  // Client-side filter + sort within the current page
  // (searching across all pages would require an additional server-side full-text index,
  //  which Redis ZSet doesn't support natively – so we note this limitation clearly)
  const items = useMemo(() => {
    if (!data?.items) return [];
    let rows = data.items.filter(d =>
      search ? d.name.toLowerCase().includes(search.toLowerCase()) : true
    );
    rows = [...rows].sort((a, b) => {
      const aVal = a[sortKey] as number | string;
      const bVal = b[sortKey] as number | string;
      if (typeof aVal === 'string') {
        return sortDir === 'asc' ? (aVal as string).localeCompare(bVal as string)
                                 : (bVal as string).localeCompare(aVal as string);
      }
      return sortDir === 'asc' ? (aVal as number) - (bVal as number)
                               : (bVal as number) - (aVal as number);
    });
    return rows;
  }, [data, search, sortKey, sortDir]);

  const handleSort = (key: keyof UsageDimensionResponse) => {
    if (sortKey === key) setSortDir(d => d === 'asc' ? 'desc' : 'asc');
    else { setSortKey(key); setSortDir('desc'); }
  };

  const SortIcon = ({ col }: { col: keyof UsageDimensionResponse }) =>
    sortKey !== col
      ? <span style={{ color: 'var(--color-text-muted)', marginLeft: 3 }}>⇅</span>
      : <span style={{ color: 'var(--color-accent)', marginLeft: 3 }}>{sortDir === 'asc' ? '↑' : '↓'}</span>;

  const totalPages = data?.totalPages ?? 1;
  const total = data?.total ?? 0;

  return (
    <div className="card" style={{ marginBottom: 'var(--space-lg)' }}>
      {/* Header */}
      <div className="card-header">
        <h3>{title}</h3>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-md)' }}>
          <input
            className="form-input"
            style={{ width: 190, height: 28, padding: '0 8px', fontSize: 11 }}
            placeholder="🔍  Filter current page..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          <select
            className="form-select"
            style={{ width: 'auto', height: 28, fontSize: 11, padding: '0 24px 0 8px' }}
            value={pageSize}
            onChange={e => { setPageSize(Number(e.target.value)); setPage(1); }}
          >
            {PAGE_SIZE_OPTIONS.map(n => (
              <option key={n} value={n}>{n} / page</option>
            ))}
          </select>
          <span className="badge neutral" style={{ whiteSpace: 'nowrap' }}>
            {total.toLocaleString()} total
          </span>
          {loading && <div className="loading-spinner" style={{ width: 14, height: 14, borderWidth: 2 }} />}
        </div>
      </div>

      {/* Table */}
      <div className="card-body" style={{ padding: 0 }}>
        {total > 0 ? (
          <>
            <div className="data-table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th style={{ width: 50 }}>
                      Rank
                    </th>
                    <th style={{ cursor: 'pointer' }} onClick={() => handleSort('name')}>
                      Name <SortIcon col="name" />
                    </th>
                    <th style={{ cursor: 'pointer' }} onClick={() => handleSort('receivedEps')}>
                      Received EPS <SortIcon col="receivedEps" />
                    </th>
                    <th style={{ cursor: 'pointer' }} onClick={() => handleSort('acceptedEps')}>
                      Accepted EPS <SortIcon col="acceptedEps" />
                    </th>
                    <th style={{ cursor: 'pointer' }} onClick={() => handleSort('droppedEps')}>
                      Dropped EPS <SortIcon col="droppedEps" />
                    </th>
                    <th style={{ width: 180 }}>Traffic</th>
                    <th style={{ cursor: 'pointer' }} onClick={() => handleSort('receivedCount')}>
                      Volume (total) <SortIcon col="receivedCount" />
                    </th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {items.length > 0 ? items.map((item, i) => {
                    const rank = (page - 1) * pageSize + i + 1;
                    const total = item.receivedEps || 1;
                    const acceptedPct = (item.acceptedEps / total) * 100;
                    const droppedPct  = (item.droppedEps  / total) * 100;
                    const hasDropped  = item.droppedEps > 0;
                    const color = colors[(rank - 1) % colors.length];

                    return (
                      <tr key={item.name}>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                            <div style={{
                              width: 10, height: 10, borderRadius: 2,
                              background: color, flexShrink: 0,
                            }} />
                            <span style={{ color: 'var(--color-text-muted)', fontSize: 11 }}>#{rank}</span>
                          </div>
                        </td>
                        <td style={{ fontWeight: 600, fontFamily: 'monospace', fontSize: 12 }}>
                          {item.name}
                        </td>
                        <td style={{ color: 'var(--color-text-secondary)' }}>
                          {item.receivedEps} <span style={{ color: 'var(--color-text-muted)' }}>/s</span>
                        </td>
                        <td style={{ fontWeight: 600, color: 'var(--color-success)' }}>
                          {item.acceptedEps} <span style={{ color: 'var(--color-text-muted)', fontWeight: 400 }}>/s</span>
                        </td>
                        <td style={{ color: hasDropped ? 'var(--color-warning)' : 'var(--color-text-muted)' }}>
                          {hasDropped
                            ? <>{item.droppedEps} <span style={{ color: 'var(--color-text-muted)', fontWeight: 400 }}>/s</span></>
                            : '—'}
                        </td>
                        <td>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                            <div className="progress-bar" style={{ height: 6 }}>
                              <div className="progress-bar-fill success" style={{ width: `${Math.min(acceptedPct, 100)}%` }} />
                              {droppedPct > 0 && <div className="progress-bar-fill warning" style={{ width: `${Math.min(droppedPct, 100)}%` }} />}
                            </div>
                            <div style={{ display: 'flex', gap: 8, fontSize: 10, color: 'var(--color-text-muted)' }}>
                              <span style={{ color: 'var(--color-success)' }}>✓ {Math.round(acceptedPct)}%</span>
                              {hasDropped && <span style={{ color: 'var(--color-warning)' }}>⚠ {Math.round(droppedPct)}%</span>}
                            </div>
                          </div>
                        </td>
                        <td style={{ fontFamily: 'monospace', fontSize: 11, color: 'var(--color-text-secondary)' }}>
                          {item.receivedCount.toLocaleString()}
                        </td>
                        <td>
                          <span className={`badge ${hasDropped ? 'warning' : 'ok'}`}>
                            {hasDropped ? 'PARTIAL' : 'OK'}
                          </span>
                        </td>
                      </tr>
                    );
                  }) : (
                    <tr>
                      <td colSpan={8}>
                        <div className="empty-state"><p>No results for "{search}" on this page</p></div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination footer */}
            {totalPages > 1 && (
              <div style={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                padding: 'var(--space-base) var(--space-lg)',
                borderTop: '1px solid var(--color-border)',
                fontSize: 'var(--font-size-xs)',
                color: 'var(--color-text-muted)',
              }}>
                <span>
                  Page <strong style={{ color: 'var(--color-text-primary)' }}>{page}</strong> of{' '}
                  <strong style={{ color: 'var(--color-text-primary)' }}>{totalPages}</strong>
                  {' '}·{' '}
                  <strong style={{ color: 'var(--color-text-primary)' }}>{data?.total.toLocaleString()}</strong> records total
                </span>
                <div style={{ display: 'flex', gap: 4 }}>
                  <button className="btn btn-secondary btn-sm" disabled={page === 1} onClick={() => setPage(1)}>«</button>
                  <button className="btn btn-secondary btn-sm" disabled={page === 1} onClick={() => setPage(p => p - 1)}>‹ Prev</button>

                  {Array.from({ length: totalPages }, (_, i) => i + 1)
                    .filter(p => p === 1 || p === totalPages || Math.abs(p - page) <= 2)
                    .reduce<(number | '...')[]>((acc, p, idx, arr) => {
                      if (idx > 0 && (p as number) - (arr[idx - 1] as number) > 1) acc.push('...');
                      acc.push(p);
                      return acc;
                    }, [])
                    .map((p, idx) =>
                      p === '...'
                        ? <span key={`ell-${idx}`} style={{ padding: '3px 6px', color: 'var(--color-text-muted)' }}>…</span>
                        : <button
                            key={p}
                            className={`btn btn-sm ${page === p ? 'btn-primary' : 'btn-secondary'}`}
                            onClick={() => setPage(p as number)}
                          >{p}</button>
                    )}

                  <button className="btn btn-secondary btn-sm" disabled={page === totalPages} onClick={() => setPage(p => p + 1)}>Next ›</button>
                  <button className="btn btn-secondary btn-sm" disabled={page === totalPages} onClick={() => setPage(totalPages)}>»</button>
                </div>
              </div>
            )}
          </>
        ) : (
          <div className="empty-state">
            <div className="empty-state-icon">📭</div>
            <p>No data yet for this window</p>
          </div>
        )}
      </div>
    </div>
  );
}

// ── Main Dashboard ────────────────────────────────────────────────────

export default function TenantDashboard() {
  const { tenantId } = useParams<{ tenantId: string }>();
  const { isAdmin } = useAuth();
  const [usage, setUsage]     = useState<UsageResponse | null>(null);
  const [history, setHistory] = useState<UsageHistoryResponse | null>(null);
  const [licenses, setLicenses] = useState<License[]>([]);
  const [alerts, setAlerts]   = useState<Alert[]>([]);
  const [loading, setLoading] = useState(true);
  const historyWindow = '15m';

  const fetchData = async () => {
    if (!tenantId) return;
    try {
      const [usageData, historyData, licensesData, alertsData] = await Promise.all([
        usageApi.getCurrent(tenantId),
        usageApi.getHistory(tenantId, '15m', 96), // 96 * 15m = 24 hours
        licensesApi.getByTenant(tenantId),
        alertsApi.getAll({ tenantId, status: 'OPEN' }),
      ]);
      setUsage(usageData);
      setHistory(historyData);
      setLicenses(licensesData);
      setAlerts(alertsData);
    } catch (err) {
      console.error('Failed to load tenant dashboard:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 5000);
    return () => clearInterval(interval);
  }, [tenantId, historyWindow]);

  // ── Export CSV state ─────────────────────────────────────────────────
  const now = new Date();
  const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  const [exportMonth, setExportMonth] = useState(currentMonth);
  const [exporting, setExporting] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  // Generate last 12 months for picker
  const monthOptions = useMemo(() => {
    const opts: string[] = [];
    const d = new Date();
    for (let i = 0; i < 12; i++) {
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, '0');
      opts.push(`${y}-${m}`);
      d.setMonth(d.getMonth() - 1);
    }
    return opts;
  }, []);

  const handleExportCsv = async () => {
    if (!tenantId) return;
    setExporting(true);
    setToast(null);
    try {
      const filename = await reportsApi.downloadUsageCsv(tenantId, exportMonth);
      setToast({ message: `Downloaded ${filename}`, type: 'success' });
    } catch (err: any) {
      setToast({ message: err.message || 'Export failed', type: 'error' });
    } finally {
      setExporting(false);
      setTimeout(() => setToast(null), 4000);
    }
  };

  const activeLicense = licenses.find(l => l.status === 'ACTIVE');

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

  if (loading) {
    return (
      <>
        <div className="page-header"><h1>Tenant Dashboard</h1></div>
        <div className="page-body"><div className="loading"><div className="loading-spinner" /> Loading...</div></div>
      </>
    );
  }

  const chartData = history?.dataPoints
    ? history.dataPoints.map(dp => ({
          time: dp.timestamp.includes('T') ? dp.timestamp.split('T')[1].slice(0, 5) : dp.timestamp,
          received: dp.received,
          accepted: dp.accepted,
          dropped: dp.dropped,
        }))
    : [];

  const usagePct    = usage?.usagePercent ?? 0;
  const usageStatus = usagePct >= 100 ? 'danger' : usagePct >= 70 ? 'warning' : 'success';

  return (
    <>
      <div className="page-header">
        <div>
          <h1>{usage?.tenantName || 'Tenant'} Dashboard</h1>
          <p>Real-time EPS monitoring and usage analytics</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-base)' }}>
          <div className="live-dot"><div className="live-dot-circle" />LIVE</div>
          <select
            className="form-select"
            style={{ width: 'auto', height: 32, fontSize: 12, padding: '0 28px 0 10px' }}
            value={exportMonth}
            onChange={e => setExportMonth(e.target.value)}
          >
            {monthOptions.map(m => (
              <option key={m} value={m}>{m}</option>
            ))}
          </select>
          <button
            className="btn btn-secondary btn-sm"
            onClick={handleExportCsv}
            disabled={exporting}
            style={{ minWidth: 120, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
          >
            {exporting ? (
              <><div className="loading-spinner" style={{ width: 14, height: 14, borderWidth: 2 }} /> Exporting...</>
            ) : (
              <>📥 Export CSV</>
            )}
          </button>
        </div>
      </div>

      {/* Toast notification */}
      {toast && (
        <div style={{
          position: 'fixed', bottom: 24, right: 24, zIndex: 9999,
          padding: '12px 20px', borderRadius: 'var(--radius-md)',
          background: toast.type === 'success' ? 'var(--color-success-bg, #1a3a2a)' : 'var(--color-danger-bg, #3a1a1a)',
          color: toast.type === 'success' ? 'var(--color-success)' : 'var(--color-danger)',
          border: `1px solid ${toast.type === 'success' ? 'var(--color-success)' : 'var(--color-danger)'}`,
          fontSize: 13, fontWeight: 500,
          boxShadow: 'var(--shadow-lg)',
          animation: 'fadeIn 0.2s ease-out',
        }}>
          {toast.type === 'success' ? '✅' : '❌'} {toast.message}
        </div>
      )}

      <div className="page-body">

        {/* KPI Cards */}
        <div className="stat-cards">
          <div className={`stat-card ${usageStatus}`}>
            <div className="stat-card-label">Current EPS</div>
            <div className={`stat-card-value ${usageStatus}`}>{(usage?.currentEps ?? 0).toLocaleString()}</div>
            <div className="stat-card-sub">events / second</div>
          </div>
          <div className="stat-card info">
            <div className="stat-card-label">EPS Quota</div>
            <div className="stat-card-value info" style={{ fontSize: usage?.quota ? undefined : '1.5rem' }}>
              {usage?.quota ? usage.quota.toLocaleString() : 'No License'}
            </div>
            <div className="stat-card-sub">
              {usage?.quota ? 'max events / second' : 'Active license required'}
            </div>
          </div>
          <div className={`stat-card ${usage?.quota ? usageStatus : 'info'}`}>
            <div className="stat-card-label">Quota Usage</div>
            <div className={`stat-card-value ${usage?.quota ? usageStatus : 'info'}`}>
              {usage?.quota ? `${usagePct}%` : 'N/A'}
            </div>
            <div className="stat-card-sub" style={{ marginTop: 6 }}>
              <div className="progress-bar" style={{ height: 4 }}>
                <div className={`progress-bar-fill ${usage?.quota ? usageStatus : 'info'}`} style={{ width: usage?.quota ? `${Math.min(usagePct, 100)}%` : '0%' }} />
              </div>
            </div>
          </div>
          <div className="stat-card success">
            <div className="stat-card-label">Accepted Today</div>
            <div className="stat-card-value success">{(usage?.acceptedToday ?? 0).toLocaleString()}</div>
            <div className="stat-card-sub">events accepted</div>
          </div>
          <div className="stat-card danger">
            <div className="stat-card-label">Dropped Today</div>
            <div className="stat-card-value danger">{(usage?.droppedToday ?? 0).toLocaleString()}</div>
            <div className="stat-card-sub">events dropped</div>
          </div>
        </div>

        {/* Chart */}
        <div className="card" style={{ marginBottom: 'var(--space-lg)' }}>
          <div className="card-header">
            <h3>EPS Timeline</h3>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-md)' }}>
              <span className="filter-tag active">24 Hours</span>
            </div>
          </div>
          <div className="card-body">
            <div className="chart-container">
              {chartData.length > 0 ? (
                <ResponsiveContainer width="100%" height={280}>
                  <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                    <defs>
                      {[
                        { id: 'received', color: 'var(--color-chart-received)' },
                        { id: 'accepted', color: 'var(--color-chart-accepted)' },
                        { id: 'dropped',  color: 'var(--color-chart-dropped)' },
                      ].map(g => (
                        <linearGradient key={g.id} id={`grad-${g.id}`} x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%"   stopColor={g.color} stopOpacity={0.25} />
                          <stop offset="100%" stopColor={g.color} stopOpacity={0.02} />
                        </linearGradient>
                      ))}
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                    <XAxis dataKey="time" stroke="var(--color-text-muted)" fontSize={10} interval="preserveStartEnd" tickLine={false} axisLine={false} />
                    <YAxis stroke="var(--color-text-muted)" fontSize={10} tickLine={false} axisLine={false} />
                    <Tooltip content={<CustomTooltip />} />
                    <Legend wrapperStyle={{ fontSize: 11, paddingTop: 12 }} iconType="square" iconSize={8} />
                    <Area type="monotone" dataKey="received" name="received" stroke="var(--color-chart-received)" fill="url(#grad-received)" strokeWidth={2} dot={false} activeDot={{ r: 4, strokeWidth: 0 }} />
                    <Area type="monotone" dataKey="accepted" name="accepted" stroke="var(--color-chart-accepted)" fill="url(#grad-accepted)" strokeWidth={2} dot={false} activeDot={{ r: 4, strokeWidth: 0 }} />
                    <Area type="monotone" dataKey="dropped"  name="dropped"  stroke="var(--color-chart-dropped)"  fill="url(#grad-dropped)"  strokeWidth={2} dot={false} activeDot={{ r: 4, strokeWidth: 0 }} />
                  </AreaChart>
                </ResponsiveContainer>
              ) : (
                <div className="empty-state"><div className="empty-state-icon">📊</div><p>No history data yet</p></div>
              )}
            </div>
          </div>
        </div>

        {/* License Info + Alerts */}
        <div className="grid-2" style={{ marginBottom: 'var(--space-lg)' }}>
          <div className="card">
            <div className="card-header"><h3>License</h3></div>
            <div className="card-body">
              {activeLicense ? (
                <>
                  <div className="info-row">
                    <span className="info-row-label">EPS Quota</span>
                    <span className="info-row-value" style={{ fontWeight: 700, color: 'var(--color-accent)' }}>
                      {activeLicense.epsQuota.toLocaleString()} EPS
                    </span>
                  </div>
                  <div className="info-row">
                    <span className="info-row-label">Start Date</span>
                    <span className="info-row-value">{activeLicense.startDate}</span>
                  </div>
                  <div className="info-row">
                    <span className="info-row-label">End Date</span>
                    <span className="info-row-value">{activeLicense.endDate}</span>
                  </div>
                  <div className="info-row">
                    <span className="info-row-label">Status</span>
                    <span className="info-row-value"><span className="badge success">{activeLicense.status}</span></span>
                  </div>
                </>
              ) : (
                <div className="empty-state"><div className="empty-state-icon">🔑</div><p>No active license</p></div>
              )}
            </div>
          </div>

          <div className="card">
            <div className="card-header">
              <h3>Open Alerts</h3>
              <span className="badge" style={{
                background: alerts.length > 0 ? 'var(--color-danger-bg)' : 'var(--color-success-bg)',
                color: alerts.length > 0 ? 'var(--color-danger)' : 'var(--color-success)',
              }}>{alerts.length}</span>
            </div>
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
                    {isAdmin && (
                      <div style={{ display: 'flex', gap: '8px', marginLeft: 'auto' }}>
                        <button className="btn btn-sm" onClick={() => handleResolveAlert(alert.alertId)} style={{ fontSize: '11px', padding: '4px 8px' }}>Resolve</button>
                        <button className="btn btn-sm btn-secondary" onClick={() => handleIgnoreAlert(alert.alertId)} style={{ fontSize: '11px', padding: '4px 8px' }}>Ignore</button>
                      </div>
                    )}
                  </div>
                ))
              ) : (
                <div className="empty-state"><div className="empty-state-icon">✅</div><p>No open alerts</p></div>
              )}
            </div>
          </div>
        </div>

        {/* Full server-side paginated dimension tables */}
        {tenantId && (
          <>
            <DimensionTable
              title={`Top Log Sources (Last 15m)`}
              tenantId={tenantId}
              dimension="logsource"
              window={historyWindow}
              colors={SOURCE_COLORS}
            />
            <DimensionTable
              title={`Top Agents (Last 15m)`}
              tenantId={tenantId}
              dimension="agent"
              window={historyWindow}
              colors={AGENT_COLORS}
            />
          </>
        )}

      </div>
    </>
  );
}
