import { useEffect, useState } from 'react';
import { tenantsApi, type Tenant } from '../../api/tenants';
import { Link } from 'react-router-dom';

export default function TenantManagement() {
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editTenant, setEditTenant] = useState<Tenant | null>(null);
  const [name, setName] = useState('');
  const [notificationEmail, setNotificationEmail] = useState('');
  const [webhookUrl, setWebhookUrl] = useState('');
  const [username, setUsername] = useState('');

  const fetchTenants = async () => {
    try {
      const data = await tenantsApi.getAll();
      setTenants(data);
    } catch (err) {
      console.error('Failed to load tenants:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchTenants(); }, []);

  const openCreate = () => { 
    setEditTenant(null); 
    setName(''); 
    setNotificationEmail('');
    setWebhookUrl('');
    setUsername('');
    setShowModal(true); 
  };
  
  const openEdit = (t: Tenant) => { 
    setEditTenant(t); 
    setName(t.name); 
    setNotificationEmail(t.notificationEmail || '');
    setWebhookUrl(t.webhookUrl || '');
    setShowModal(true); 
  };

  const handleSubmit = async () => {
    if (!name.trim()) return;
    try {
      if (editTenant) {
        await tenantsApi.update(editTenant.tenantId, name, notificationEmail, webhookUrl);
      } else {
        await tenantsApi.create(name, notificationEmail, webhookUrl, username);
      }
      setShowModal(false);
      fetchTenants();
    } catch (err) {
      console.error('Failed to save tenant:', err);
    }
  };

  const handleDisable = async (id: string) => {
    if (!confirm('Disable this tenant?')) return;
    await tenantsApi.disable(id);
    fetchTenants();
  };

  if (loading) {
    return (
      <>
        <div className="page-header"><h1>Tenant Management</h1></div>
        <div className="page-body"><div className="loading"><div className="loading-spinner" />Loading...</div></div>
      </>
    );
  }

  return (
    <>
      <div className="page-header">
        <h1>Tenant Management</h1>
        <p>Create and manage SOC tenants</p>
      </div>
      <div className="page-body">
        <div style={{ marginBottom: 'var(--space-base)', display: 'flex', justifyContent: 'flex-end' }}>
          <button className="btn btn-primary" onClick={openCreate}>+ Create Tenant</button>
        </div>

        <div className="card">
          <div className="card-body" style={{ padding: 0 }}>
            <div className="data-table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Status</th>
                    <th>Created</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {tenants.map(t => (
                    <tr key={t.tenantId}>
                      <td><Link to={`/admin/tenants/${t.tenantId}`}>{t.name}</Link></td>
                      <td>
                        <span className={`badge ${t.status === 'ACTIVE' ? 'success' : 'neutral'}`}>
                          {t.status}
                        </span>
                      </td>
                      <td>{new Date(t.createdAt).toLocaleDateString()}</td>
                      <td>
                        <div className="btn-group">
                          <button className="btn btn-secondary btn-sm" onClick={() => openEdit(t)}>Edit</button>
                          {t.status === 'ACTIVE' && (
                            <button className="btn btn-danger btn-sm" onClick={() => handleDisable(t.tenantId)}>Disable</button>
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
              <h2>{editTenant ? 'Edit Tenant' : 'Create Tenant'}</h2>
              <button className="modal-close" onClick={() => setShowModal(false)}>×</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label">Tenant Name</label>
                <input className="form-input" value={name} onChange={e => setName(e.target.value)} placeholder="Enter tenant name" autoFocus />
              </div>
              <div className="form-group">
                <label className="form-label">Notification Email</label>
                <input className="form-input" type="email" value={notificationEmail} onChange={e => setNotificationEmail(e.target.value)} placeholder="admin@tenant.com (optional)" />
              </div>
              <div className="form-group">
                <label className="form-label">Webhook URL (Slack/Telegram)</label>
                <input className="form-input" type="url" value={webhookUrl} onChange={e => setWebhookUrl(e.target.value)} placeholder="https://... (optional)" />
              </div>
              {!editTenant && (
                <div className="form-group">
                  <label className="form-label">Keycloak Username</label>
                  <input className="form-input" value={username} onChange={e => setUsername(e.target.value)} placeholder="Leave blank to auto-generate" />
                </div>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleSubmit}>{editTenant ? 'Update' : 'Create'}</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
