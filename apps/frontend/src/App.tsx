import { BrowserRouter, Routes, Route, Navigate, useParams } from 'react-router-dom';
import './App.css';
import AdminLayout from './layouts/AdminLayout';
import TenantLayout from './layouts/TenantLayout';
import AdminDashboard from './pages/admin/AdminDashboard';
import TenantManagement from './pages/admin/TenantManagement';
import LicenseManagement from './pages/admin/LicenseManagement';
import AuditLogPage from './pages/admin/AuditLogPage';
import TenantDashboard from './pages/tenant/TenantDashboard';
import { useAuth } from './context/AuthContext';

function ProtectedAdminRoute({ children }: { children: React.ReactNode }) {
  const { isAdmin, tenantId } = useAuth();
  if (!isAdmin) {
    return <Navigate to={`/tenant/${tenantId}`} replace />;
  }
  return <>{children}</>;
}

function ProtectedTenantRoute({ children }: { children: React.ReactNode }) {
  const { isAdmin, tenantId: userTenantId } = useAuth();
  const { tenantId: paramTenantId } = useParams<{ tenantId: string }>();

  if (!isAdmin && String(userTenantId) !== String(paramTenantId)) {
    return <Navigate to={`/tenant/${userTenantId}`} replace />;
  }

  if (isAdmin && (!paramTenantId || paramTenantId === 'null' || paramTenantId === 'undefined')) {
    return <Navigate to="/admin" replace />;
  }

  return <>{children}</>;
}

function App() {
  const { isAdmin, tenantId } = useAuth();

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to={isAdmin ? "/admin" : `/tenant/${tenantId}`} replace />} />
        
        {/* Admin Routes */}
        <Route path="/admin" element={<ProtectedAdminRoute><AdminLayout /></ProtectedAdminRoute>}>
          <Route index element={<AdminDashboard />} />
          <Route path="tenants" element={<TenantManagement />} />
          <Route path="tenants/:tenantId" element={<TenantDashboard />} />
          <Route path="licenses" element={<LicenseManagement />} />
          <Route path="audit-logs" element={<AuditLogPage />} />
        </Route>

        {/* Tenant Routes */}
        <Route path="/tenant/:tenantId" element={<ProtectedTenantRoute><TenantLayout /></ProtectedTenantRoute>}>
          <Route index element={<TenantDashboard />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;