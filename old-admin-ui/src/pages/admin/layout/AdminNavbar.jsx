import React, { useEffect, useState } from 'react';
import { logout } from '../../../api/authService.js';
import { adminService } from '../../../api/adminService.js';
// countdown moved to admin sidebar

const AdminNavbar = ({ user, onLogout, offcanvasTarget = '#adminOffcanvas' }) => {
  const [profile, setProfile] = useState(null);
  // session countdown is handled in the sidebar now

  // Load profile and start session countdown
  useEffect(() => {
    let timer;
    (async () => {
      try {
        const p = await adminService.me();
        setProfile(p);
      } catch {
        // ignore profile load
      }
  // navbar no longer displays session countdown; it's shown in sidebar
    })();
    return () => clearInterval(timer);
  }, []);
  const handleLogout = async () => {
    try {
      await logout();
      onLogout?.();
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <nav className="navbar navbar-expand-lg navbar-dark bg-primary fixed-top">
      <div className="container-fluid">
        <button className="btn btn-outline-light d-lg-none" type="button" data-bs-toggle="offcanvas" data-bs-target={offcanvasTarget} aria-controls={offcanvasTarget.substring(1)}>
          <i className="bi bi-list"></i>
        </button>
        <span className="navbar-brand ms-2">Admin</span>
        <div className="navbar-nav ms-auto align-items-center gap-2">
          <div className="nav-item dropdown">
            <a className="nav-link dropdown-toggle text-white" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">
              <i className="bi bi-person-circle me-1"></i>{user?.username}
            </a>
            <ul className="dropdown-menu dropdown-menu-end">
              <li><span className="dropdown-item-text"><small className="text-muted">Role: {user?.role}</small></span></li>
              {profile?.id && (
                <li><span className="dropdown-item-text"><small className="text-muted">ID: {profile.id}</small></span></li>
              )}
              <li><hr className="dropdown-divider" /></li>
              <li><button className="dropdown-item" onClick={handleLogout}><i className="bi bi-box-arrow-right me-2"></i>Logout</button></li>
            </ul>
          </div>
        </div>
      </div>
    </nav>
  );
};

export default AdminNavbar;
