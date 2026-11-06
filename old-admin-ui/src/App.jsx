import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './components/Login.jsx';
import AdminDashboard from './pages/admin/AdminDashboard.jsx';
import AdminLayout from './pages/admin/layout/AdminLayout.jsx';
import AdminApps from './pages/admin/AdminApps.jsx';
import AdminUsers from './pages/admin/AdminUsers.jsx';
import AdminUserAppData from './pages/admin/AdminUserAppData.jsx';
import AdminProfile from './pages/admin/AdminProfile.jsx';
import AdminSupport from './pages/admin/AdminSupport.jsx';
import OwnerDashboard from './pages/owner/OwnerDashboard.jsx';
import OwnerLayout from './pages/owner/layout/OwnerLayout.jsx';
import OwnerCategories from './pages/owner/OwnerCategories.jsx';
import OwnerAdmins from './pages/owner/OwnerAdmins.jsx';
import OwnerAdminPanelViewer from './pages/owner/OwnerAdminPanelViewer.jsx';
import { AdminRoute, OwnerRoute, GuestRoute } from './components/ProtectedRoute.jsx';
import { isAuthenticated, getDashboardRoute, getStoredToken } from './utils/jwt.js';
import 'bootstrap/dist/css/bootstrap.min.css';
import './App.css';

function App() {
  // Function to determine where to redirect authenticated users
  const getAuthenticatedRedirect = () => {
    if (isAuthenticated()) {
      const token = getStoredToken();
      return getDashboardRoute(token);
    }
    return '/login';
  };

  return (
    <Router>
      <div className="App">
        <Routes>
          {/* Public Routes (Guest Only) */}
          <Route
            path="/login"
            element={
              <GuestRoute>
                <Login />
              </GuestRoute>
            }
          />

          {/* Protected Admin Routes with nested layout */}
          <Route
            path="/admin"
            element={
              <AdminRoute>
                <AdminLayout />
              </AdminRoute>
            }
          >
            <Route index element={<AdminDashboard />} />
            <Route path="apps" element={<AdminApps />} />
            <Route path="users" element={<AdminUsers />} />
            <Route path="user-app-data" element={<AdminUserAppData />} />
            <Route path="profile" element={<AdminProfile />} />
            <Route path="support" element={<AdminSupport />} />
          </Route>

          {/* Protected Owner Routes with nested layout */}
          <Route
            path="/owner"
            element={
              <OwnerRoute>
                <OwnerLayout />
              </OwnerRoute>
            }
          >
            <Route index element={<OwnerDashboard />} />
            <Route path="categories" element={<OwnerCategories />} />
            <Route path="admins" element={<OwnerAdmins />} />
            <Route path="admin-panel-viewer" element={<OwnerAdminPanelViewer />} />
          </Route>

          {/* Root Route - Redirect based on auth status */}
          <Route
            path="/"
            element={<Navigate to={getAuthenticatedRedirect()} replace />}
          />

          {/* Back-compat: redirect old explicit dashboard paths to new index routes */}
          <Route path="/admin/dashboard" element={<Navigate to="/admin" replace />} />
          <Route path="/owner/dashboard" element={<Navigate to="/owner" replace />} />

          {/* Catch-all Route - Redirect to appropriate dashboard or login */}
          <Route
            path="*"
            element={<Navigate to={getAuthenticatedRedirect()} replace />}
          />
        </Routes>
      </div>
    </Router>
  );
}

export default App;
