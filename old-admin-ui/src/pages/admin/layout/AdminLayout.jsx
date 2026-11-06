import React, { useEffect, useState } from 'react';
import * as bootstrap from 'bootstrap';
import { Outlet, useNavigate } from 'react-router-dom';
import AdminNavbar from './AdminNavbar.jsx';
import AdminSidebar from './AdminSidebar.jsx';
import { getCurrentUser } from '../../../utils/jwt.js';

const AdminLayout = () => {
  const [user, setUser] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    setUser(getCurrentUser());
  }, []);

  const afterLogout = () => navigate('/login', { replace: true });

  const closeOffcanvas = () => {
    const el = document.getElementById('adminOffcanvas');
    if (!el) return;
    const offcanvas = bootstrap?.Offcanvas?.getInstance(el) || new bootstrap.Offcanvas(el);
    offcanvas.hide();
  };

  return (
  <div className="min-vh-100 bg-light" style={{ paddingTop: '56px' }}>
      <AdminNavbar user={user} onLogout={afterLogout} offcanvasTarget="#adminOffcanvas" />

      <div className="offcanvas offcanvas-start" tabIndex="-1" id="adminOffcanvas" aria-labelledby="adminOffcanvasLabel">
        <div className="offcanvas-header">
          <h5 className="offcanvas-title" id="adminOffcanvasLabel">Menu</h5>
          <button type="button" className="btn-close" data-bs-dismiss="offcanvas" aria-label="Close"></button>
        </div>
        <div className="offcanvas-body">
          <AdminSidebar onNavigate={closeOffcanvas} />
        </div>
      </div>

      <div className="container-fluid">
        <div className="row">
          <aside className="col-lg-2 d-none d-lg-block bg-white shadow-sm sidebar pt-3" style={{ position: 'sticky', top: '56px', height: 'calc(100vh - 56px)', overflowY: 'auto' }}>
            <AdminSidebar />
          </aside>
          <main className="col-12 col-lg-10 px-3 px-md-4 py-3">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
};

export default AdminLayout;
