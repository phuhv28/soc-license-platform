import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function AdminLayout() {
  const { username, logout } = useAuth();
  return (
    <div className="app-layout">
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="sidebar-brand-icon">S</div>
          <div>
            <div className="sidebar-brand-text">SOC Platform</div>
            <div className="sidebar-brand-sub">License Portal</div>
          </div>
        </div>

        <nav className="sidebar-nav">
          <div className="sidebar-section-label">Overview</div>
          <NavLink to="/admin" end className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
            <span className="sidebar-link-icon">⬡</span> Dashboard
          </NavLink>

          <div className="sidebar-section-label">Management</div>
          <NavLink to="/admin/tenants" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
            <span className="sidebar-link-icon">🏢</span> Tenants
          </NavLink>
          <NavLink to="/admin/licenses" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
            <span className="sidebar-link-icon">🔑</span> Licenses
          </NavLink>
          <NavLink to="/admin/audit-logs" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
            <span className="sidebar-link-icon">📋</span> Audit Logs
          </NavLink>
        </nav>

        <div className="sidebar-footer">
          <div className="sidebar-footer-avatar">{username.charAt(0).toUpperCase()}</div>
          <div className="sidebar-footer-info" style={{ flex: 1 }}>
            <div className="sidebar-footer-name">{username}</div>
            <div className="sidebar-footer-role">System Admin</div>
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

      {/* Main Content */}
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}
