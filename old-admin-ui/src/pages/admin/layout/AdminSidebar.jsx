import React, { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import { adminService } from '../../../api/adminService.js';
import { getStoredToken, getTokenTimeRemaining, formatHMS } from '../../../utils/jwt.js';

const AdminSidebar = ({ onNavigate }) => {
  const [profile, setProfile] = useState(null);
  const [sessionSeconds, setSessionSeconds] = useState(0);

  useEffect(() => {
    let timer;
    (async () => {
      try {
        const p = await adminService.me();
        setProfile(p);
      } catch {
        // ignore
      }
      const update = () => setSessionSeconds(getTokenTimeRemaining(getStoredToken()));
      update();
      timer = setInterval(update, 1000);
    })();
    return () => clearInterval(timer);
  }, []);
  const linkClass = ({ isActive }) => `nav-link d-flex align-items-center ${isActive ? 'active fw-semibold' : 'text-body-secondary'}`;
  const handleClick = () => {
    if (onNavigate) onNavigate();
  };
  return (
    <div className="d-flex flex-column h-100">
      <ul className="nav flex-column">
      <li className="nav-item"><NavLink to="/admin" end className={linkClass} onClick={handleClick}><i className="bi bi-house-door me-2"></i>Dashboard</NavLink></li>
      <li className="nav-item"><NavLink to="/admin/apps" className={linkClass} onClick={handleClick}><i className="bi bi-app-indicator me-2"></i>App Management</NavLink></li>
      <li className="nav-item"><NavLink to="/admin/users" className={linkClass} onClick={handleClick}><i className="bi bi-people me-2"></i>User Management</NavLink></li>
      <li className="nav-item"><NavLink to="/admin/user-app-data" className={linkClass} onClick={handleClick}><i className="bi bi-clipboard-data me-2"></i>User Device Data</NavLink></li>
      <li className="nav-item"><NavLink to="/admin/profile" className={linkClass} onClick={handleClick}><i className="bi bi-person-circle me-2"></i>Profile</NavLink></li>
      <li className="nav-item"><NavLink to="/admin/support" className={linkClass} onClick={handleClick}><i className="bi bi-life-preserver me-2"></i>Help & Support</NavLink></li>
      </ul>
      <div className="flex-grow-1" />
      <div className="mt-3">
        <div className="list-group list-group-flush rounded-3 border overflow-hidden">
          <div className="list-group-item d-flex align-items-center py-2">
            <span className="badge rounded-pill text-bg-primary-subtle text-primary-emphasis me-2">
              <i className="bi bi-hourglass-split"></i>
            </span>
            <div className="small text-secondary">Session</div>
            <div className="ms-auto fw-semibold small">{formatHMS(sessionSeconds)}</div>
          </div>
          <div className="list-group-item d-flex align-items-center py-2">
            <span className="badge rounded-pill text-bg-info-subtle text-info-emphasis me-2">
              <i className="bi bi-calendar2-event"></i>
            </span>
            <div className="small text-secondary">Account</div>
            <div className="ms-auto fw-semibold small">{profile?.expirySeconds != null ? formatHMS(profile.expirySeconds) : 'nana'}</div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AdminSidebar;
