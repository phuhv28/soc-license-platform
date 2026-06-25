import { NavLink, Outlet, useParams } from 'react-router-dom';

export default function TenantLayout() {
  const { tenantId } = useParams<{ tenantId: string }>();

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
      </aside>
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}
