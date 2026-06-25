import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import './App.css';
import AdminLayout from './layouts/AdminLayout';
import TenantLayout from './layouts/TenantLayout';
import AdminDashboard from './pages/admin/AdminDashboard';
import TenantManagement from './pages/admin/TenantManagement';
import LicenseManagement from './pages/admin/LicenseManagement';
import AuditLogPage from './pages/admin/AuditLogPage';
import TenantDashboard from './pages/tenant/TenantDashboard';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/admin" replace />} />
        
        {/* Admin Routes */}
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<AdminDashboard />} />
          <Route path="tenants" element={<TenantManagement />} />
          <Route path="licenses" element={<LicenseManagement />} />
          <Route path="audit-logs" element={<AuditLogPage />} />
        </Route>

        {/* Tenant Routes */}
        <Route path="/tenant/:tenantId" element={<TenantLayout />}>
          <Route index element={<TenantDashboard />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;