import { useEffect, useState } from 'react';
import { licensesApi, type License, type CreateLicenseRequest, type UpdateLicenseRequest } from '../../api/licenses';
import { tenantsApi, type Tenant } from '../../api/tenants';

export default function LicenseManagement() {
  const [licenses, setLicenses] = useState<License[]>([]);
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editLicense, setEditLicense] = useState<License | null>(null);

  // Form state
  const [tenantId, setTenantId] = useState('');
  const [epsQuota, setEpsQuota] = useState(100);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [status, setStatus] = useState('ACTIVE');

  const fetchData = async () => {
    try {
      const [lics, tns] = await Promise.all([licensesApi.getAll(), tenantsApi.getAll()]);
      setLicenses(lics);
      setTenants(tns);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchData(); }, []);

  const openCreate = () => {
    setEditLicense(null);
    setTenantId(tenants.find(t => t.status === 'ACTIVE')?.tenantId || '');
    setEpsQuota(100);
    setStartDate(new Date().toISOString().split('T')[0]);
    setEndDate('');
    setStatus('ACTIVE');
    setShowModal(true);
  };

  const openEdit = (lic: License) => {
    setEditLicense(lic);
    setTenantId(lic.tenantId);
    setEpsQuota(lic.epsQuota);
    setStartDate(lic.startDate);
    setEndDate(lic.endDate);
    setStatus(lic.status);
    setShowModal(true);
  };

  const handleSubmit = async () => {
    try {
      if (editLicense) {
        const req: UpdateLicenseRequest = { epsQuota, startDate, endDate, status };
        await licensesApi.update(editLicense.licenseId, req);
      } else {
        const req: CreateLicenseRequest = { tenantId, epsQuota, startDate, endDate };
        await licensesApi.create(req);
      }
      setShowModal(false);
      fetchData();
    } catch (err) { console.error(err); }
  };

  const handleDisable = async (id: string) => {
    if (!confirm('Disable this license?')) return;
    await licensesApi.disable(id);
    fetchData();
  };

  const getTenantName = (tid: string) => tenants.find(t => t.tenantId === tid)?.name || tid.slice(0, 8);

  if (loading) {
    return (
      <>
        <div className="page-header"><h1>License Management</h1></div>
        <div className="page-body"><div className="loading"><div className="loading-spinner" />Loading...</div></div>
      </>
    );
  }

  return (
    <>
      <div className="page-header">
        <h1>License Management</h1>
        <p>Create and manage EPS licenses</p>
      </div>
      <div className="page-body">
        <div style={{ marginBottom: 'var(--space-base)', display: 'flex', justifyContent: 'flex-end' }}>
          <button className="btn btn-primary" onClick={openCreate}>+ Create License</button>
        </div>

        <div className="card">
          <div className="card-body" style={{ padding: 0 }}>
            <div className="data-table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Tenant</th>
                    <th>EPS Quota</th>
                    <th>Start</th>
                    <th>End</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {licenses.map(lic => (
                    <tr key={lic.licenseId}>
                      <td>{getTenantName(lic.tenantId)}</td>
                      <td><strong>{lic.epsQuota}</strong></td>
                      <td>{lic.startDate}</td>
                      <td>{lic.endDate}</td>
                      <td>
                        <span className={`badge ${lic.status === 'ACTIVE' ? 'success' : lic.status === 'EXPIRED' ? 'danger' : 'neutral'}`}>
                          {lic.status}
                        </span>
                      </td>
                      <td>
                        <div className="btn-group">
                          <button className="btn btn-secondary btn-sm" onClick={() => openEdit(lic)}>Edit</button>
                          {lic.status === 'ACTIVE' && (
                            <button className="btn btn-danger btn-sm" onClick={() => handleDisable(lic.licenseId)}>Disable</button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      {/* Modal */}
      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2>{editLicense ? 'Edit License' : 'Create License'}</h2>
              <button className="modal-close" onClick={() => setShowModal(false)}>×</button>
            </div>
            <div className="modal-body">
              {!editLicense && (
                <div className="form-group">
                  <label className="form-label">Tenant</label>
                  <select className="form-select" value={tenantId} onChange={e => setTenantId(e.target.value)}>
                    <option value="">Select tenant</option>
                    {tenants.filter(t => t.status === 'ACTIVE').map(t => (
                      <option key={t.tenantId} value={t.tenantId}>{t.name}</option>
                    ))}
                  </select>
                </div>
              )}
              <div className="form-group">
                <label className="form-label">EPS Quota</label>
                <input className="form-input" type="number" min={1} value={epsQuota} onChange={e => setEpsQuota(Number(e.target.value))} />
              </div>
              <div className="grid-2">
                <div className="form-group">
                  <label className="form-label">Start Date</label>
                  <input className="form-input" type="date" value={startDate} onChange={e => setStartDate(e.target.value)} />
                </div>
                <div className="form-group">
                  <label className="form-label">End Date</label>
                  <input className="form-input" type="date" value={endDate} onChange={e => setEndDate(e.target.value)} />
                </div>
              </div>
              {editLicense && (
                <div className="form-group">
                  <label className="form-label">Status</label>
                  <select className="form-select" value={status} onChange={e => setStatus(e.target.value)}>
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="EXPIRED">EXPIRED</option>
                    <option value="DISABLED">DISABLED</option>
                  </select>
                </div>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleSubmit}>{editLicense ? 'Update' : 'Create'}</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
