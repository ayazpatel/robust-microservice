import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { logout } from "../../api/authService.js";
import { getCurrentUser, getTokenTimeRemaining } from "../../utils/jwt.js";
import "bootstrap/dist/css/bootstrap.min.css";
import "bootstrap-icons/font/bootstrap-icons.css";

const AdminDashboard = () => {
  const [user, setUser] = useState(null);
  const [timeRemaining, setTimeRemaining] = useState(0);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    const loadUserData = () => {
      try {
        const currentUser = getCurrentUser();
        setUser(currentUser);
        updateTimeRemaining();
      } catch (error) {
        console.error("Error loading user data:", error);
      }
    };

    const updateTimeRemaining = () => {
      try {
        const token = localStorage.getItem("admin_token");
        if (token) {
          const remaining = getTokenTimeRemaining(token);
          setTimeRemaining(remaining);
        }
      } catch (error) {
        console.error("Error updating time remaining:", error);
      }
    };

    loadUserData();
    const interval = setInterval(() => {
      updateTimeRemaining();
    }, 60000);
    return () => clearInterval(interval);
  }, []);

  const handleLogout = async () => {
    setLoading(true);
    try {
      await logout();
      navigate("/login", { replace: true });
    } catch (error) {
      console.error("Logout error:", error);
    } finally {
      setLoading(false);
    }
  };

  if (!user) {
    return (
      <div className="d-flex justify-content-center align-items-center min-vh-100">
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  const loginIp =
    user?.lastLoginIp || localStorage.getItem("admin_login_ip") || "Unknown IP";
  const twoStepStatus = user?.twoFactorEnabled ? "Enabled" : "Disabled";

  return (
    <div className="min-vh-100 bg-light">
      <div className="container-fluid">
        <div className="row">
          {/* Main Content */}
          <main className="col-12 col-lg-12 px-2 px-sm-3 px-md-4 w-100">
            <div className="d-flex flex-wrap justify-content-between align-items-center pt-3 pb-2 mb-3 border-bottom">
              <h1 className="h4 h3-md mb-0">Admin Dashboard</h1>
              <small className="text-muted">
                Session expires in {Math.max(0, Math.floor(timeRemaining / 60))}
                m
              </small>
            </div>
            {/* Cards Grid */}
            <div className="row g-3">
              <div className="col-12 col-md-6 col-xl-3">
                <div className="card border-left-primary shadow h-100">
                  <div className="card-body d-flex justify-content-between align-items-center">
                    <div>
                      <div className="text-xs text-uppercase mb-1">
                        Total Users
                      </div>
                      <div className="h5 mb-0">124</div>
                    </div>
                    <i className="bi bi-people fs-2 text-primary"></i>
                  </div>
                </div>
              </div>
              <div className="col-12 col-md-6 col-xl-3">
                <div className="card border-left-success shadow h-100">
                  <div className="card-body d-flex justify-content-between align-items-center">
                    <div>
                      <div className="text-xs text-uppercase mb-1">
                        Total Visits
                      </div>
                      <div className="h5 mb-0">4520</div>
                    </div>
                    <i className="bi bi-app-indicator fs-2 text-success"></i>
                  </div>
                </div>
              </div>
              <div className="col-12 col-md-6 col-xl-3">
                <div className="card border-left-info shadow h-100">
                  <div className="card-body d-flex justify-content-between align-items-center">
                    <div>
                      <div className="text-xs text-uppercase mb-1">
                        SMS Today
                      </div>
                      <div className="h5 mb-0">1,234</div>
                    </div>
                    <i className="bi bi-chat-square-text fs-2 text-info"></i>
                  </div>
                </div>
              </div>
              <div className="col-12 col-md-6 col-xl-3">
                <div className="card border-left-warning shadow h-100">
                  <div className="card-body d-flex justify-content-between align-items-center">
                    <div>
                      <div className="text-xs text-uppercase mb-1">
                        Active Connections
                      </div>
                      <div className="h5 mb-0">100</div>
                    </div>
                    <i className="bi bi-grid-3x3-gap fs-2 text-warning"></i>
                  </div>
                </div>
              </div>
            </div>

            <div className="row g-3 mt-1">
              <div className="col-12 col-md-6 col-xl-3">
                <div className="card border-left-primary shadow h-100">
                  <div className="card-body d-flex justify-content-between align-items-center">
                    <div>
                      <div className="text-xs text-uppercase mb-1">
                        Total UPIs
                      </div>
                      <div className="h5 mb-0">2,500</div>
                    </div>
                    <i className="bi bi-qr-code fs-2 text-primary"></i>
                  </div>
                </div>
              </div>
              <div className="col-12 col-md-6 col-xl-3">
                <div className="card border-left-success shadow h-100">
                  <div className="card-body d-flex justify-content-between align-items-center">
                    <div>
                      <div className="text-xs text-uppercase mb-1">
                        Total Cards
                      </div>
                      <div className="h5 mb-0">1,150</div>
                    </div>
                    <i className="bi bi-credit-card fs-2 text-success"></i>
                  </div>
                </div>
              </div>
              <div className="col-12 col-md-6 col-xl-3">
                <div className="card border-left-info shadow h-100">
                  <div className="card-body d-flex justify-content-between align-items-center">
                    <div>
                      <div className="text-xs text-uppercase mb-1">
                        Total Banks
                      </div>
                      <div className="h5 mb-0">146</div>
                    </div>
                    <i className="bi bi-bank fs-2 text-info"></i>
                  </div>
                </div>
              </div>
              <div className="col-12 col-md-6 col-xl-3">
                <div className="card border-left-warning shadow h-100">
                  <div className="card-body d-flex justify-content-between align-items-center">
                    <div>
                      <div className="text-xs text-uppercase mb-1">
                        Total Paypals
                      </div>
                      <div className="h5 mb-0">526</div>
                    </div>
                    <i className="bi bi-wallet2 fs-2 text-warning"></i>
                  </div>
                </div>
              </div>
            </div>

            <div className="row g-3 mt-1">
              <div className="col-12 col-lg-6">
                <div className="card shadow">
                  <div className="card-header py-3">
                    <h6 className="m-0">Account Activity</h6>
                  </div>
                  <div className="card-body">
                    <div className="list-group list-group-flush">
                      <div className="list-group-item d-flex justify-content-between align-items-start">
                        <div className="ms-2 me-auto">
                          <div className="fw-bold">
                            <i className="bi bi-box-arrow-in-right me-1"></i>
                            Login
                          </div>
                          Signed in successfully
                          <div className="small text-muted">
                            IP: <code>{loginIp}</code>
                          </div>
                        </div>
                        <small>Just now</small>
                      </div>

                      <div className="list-group-item d-flex justify-content-between align-items-start">
                        <div className="ms-2 me-auto">
                          <div className="fw-bold">
                            <i className="bi bi-key me-1"></i>Password Changed
                          </div>
                          Your account password was updated
                          <div className="small text-muted">
                            IP: <code>{loginIp}</code>
                          </div>
                        </div>
                        <small>Today</small>
                      </div>

                      <div className="list-group-item d-flex justify-content-between align-items-start">
                        <div className="ms-2 me-auto">
                          <div className="fw-bold">
                            <i className="bi bi-shield-lock me-1"></i>Two‑Step
                            Verification
                          </div>
                          Status: {twoStepStatus}
                          <div className="small text-muted">
                            IP: <code>{loginIp}</code>
                          </div>
                        </div>
                        <small>Today</small>
                      </div>

                      <div className="list-group-item d-flex justify-content-between align-items-start">
                        <div className="ms-2 me-auto">
                          <div className="fw-bold">
                            <i className="bi bi-telegram me-1"></i>Telegram
                            Settings
                          </div>
                          Bot or chat configuration updated
                          <div className="small text-muted">
                            IP: <code>{loginIp}</code>
                          </div>
                        </div>
                        <small>Yesterday</small>
                      </div>

                      <div className="list-group-item d-flex justify-content-between align-items-start">
                        <div className="ms-2 me-auto">
                          <div className="fw-bold">
                            <i className="bi bi-envelope-gear me-1"></i>SMTP
                            Settings
                          </div>
                          Email delivery settings changed
                          <div className="small text-muted">
                            IP: <code>{loginIp}</code>
                          </div>
                        </div>
                        <small>2 days ago</small>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="col-12 col-lg-6">
                <div className="card shadow">
                  <div className="card-header py-3">
                    <h6 className="m-0">Application Summary</h6>
                  </div>
                  <div className="card-body">
                    <div className="d-flex justify-content-between mb-2">
                      <span>Application Services</span>
                      <span className="badge bg-success">Connected</span>
                    </div>
                    <div className="d-flex justify-content-between mb-2">
                      <span>API Services</span>
                      <span className="badge bg-success">Connected</span>
                    </div>
                    <div className="d-flex justify-content-between mb-2">
                      <span>SMS Gateway</span>
                      <span className="badge bg-success">Connected</span>
                    </div>
                    <div className="d-flex justify-content-between mb-2">
                      <span>Telegram Bot Webhook</span>
                      <span className="badge bg-success">Connected</span>
                    </div>
                    <div className="d-flex justify-content-between mb-2">
                      <span>Mail Server</span>
                      <span className="badge bg-success">Connected</span>
                    </div>
                    <div className="d-flex justify-content-between mb-2">
                      <span>Database</span>
                      <span className="badge bg-success">Connected</span>
                    </div>
                    <div className="d-flex justify-content-between mb-2">
                      <span>Rabit Technology</span>
                      <span className="badge bg-success">Connected</span>
                    </div>
                    <div className="d-flex justify-content-between">
                      <span>Storage</span>
                      <span className="badge bg-warning text-dark">78% Used</span>
                    </div>
                    
                  </div>
                </div>
              </div>
            </div>
          </main>
        </div>
      </div>
    </div>
  );
};

export default AdminDashboard;
