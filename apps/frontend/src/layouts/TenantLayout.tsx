import { NavLink, Outlet, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function TenantLayout() {
  const { tenantId } = useParams<{ tenantId: string }>();
  const { username, logout } = useAuth();

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="sidebar-brand-icon" style={{ background: 'linear-gradient(135deg, var(--color-success), #3fb950)' }}>T</div>
          <span className="sidebar-brand-text">Tenant Portal</span>
        </div>
        <nav className="sidebar-nav">
          <div className="sidebar-section-label">Menu</div>
          <NavLink to={`/tenant/${tenantId}`} end className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
            <span className="sidebar-link-icon">📊</span> Dashboard
          </NavLink>
        </nav>
        
        <div className="sidebar-footer" style={{ marginTop: 'auto' }}>
          <div className="sidebar-footer-avatar" style={{ background: 'var(--color-success-bg)', color: 'var(--color-success)' }}>
            {username.charAt(0).toUpperCase()}
          </div>
          <div className="sidebar-footer-info" style={{ flex: 1 }}>
            <div className="sidebar-footer-name">{username}</div>
            <div className="sidebar-footer-role">Tenant User</div>
          </div>
          <button 
            onClick={logout} 
            className="btn btn-sm" 
            style={{ padding: '4px 12px', background: 'var(--color-danger-bg)', border: '1px solid var(--color-danger)', color: 'var(--color-danger)' }}
          >
            Logout
          </button>
        </div>
      </aside>
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}
