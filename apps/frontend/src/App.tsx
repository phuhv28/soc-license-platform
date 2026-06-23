import { BrowserRouter, Routes, Route, Navigate, NavLink } from 'react-router-dom';
import './App.css';
import AdminDashboard from './pages/admin/AdminDashboard';
import TenantManagement from './pages/admin/TenantManagement';
import LicenseManagement from './pages/admin/LicenseManagement';
import AuditLogPage from './pages/admin/AuditLogPage';
import TenantDashboard from './pages/tenant/TenantDashboard';

function App() {
  return (
    <BrowserRouter>
      <div className="app-layout">
        {/* Sidebar */}
        <aside className="sidebar">
          <div className="sidebar-brand">
            <div className="sidebar-brand-icon">S</div>
            <span className="sidebar-brand-text">SOC Platform</span>
          </div>
          <nav className="sidebar-nav">
            <div className="sidebar-section-label">Admin</div>
            <NavLink to="/admin" end className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
              <span className="sidebar-link-icon">📊</span> Dashboard
            </NavLink>
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
        </aside>

        {/* Main Content */}
        <main className="main-content">
          <Routes>
            <Route path="/" element={<Navigate to="/admin" replace />} />
            <Route path="/admin" element={<AdminDashboard />} />
            <Route path="/admin/tenants" element={<TenantManagement />} />
            <Route path="/admin/licenses" element={<LicenseManagement />} />
            <Route path="/admin/audit-logs" element={<AuditLogPage />} />
            <Route path="/tenant/:tenantId" element={<TenantDashboard />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}

export default App;