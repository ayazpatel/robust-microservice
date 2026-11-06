import React from "react";

const AdminProfile = () => {
  const [twoFAEnabled, setTwoFAEnabled] = React.useState(false);
  const [methods, setMethods] = React.useState({
    telegram: false,
    email: false,
  });

  const toggleMethod = (name) =>
    setMethods((m) => ({ ...m, [name]: !m[name] }));

  return (
    <>
      <div className="min-vh-100 bg-light">
        <div className="container-fluid">
          <main className="col-12 col-lg-12 px-2 px-sm-3 px-md-4 w-100">
            <div className="d-flex flex-wrap justify-content-between align-items-center pt-3 pb-2 mb-3 border-bottom">
              <h1 className="h4 h3-md mb-0">Account Profile Management</h1>
            </div>

            {/* Cards Grid */}
            <div className="row">
              <main className="col-12 col-lg-12 px-2 px-sm-3 px-md-4 w-100">
                <div className="row g-3 mt-1">
                  <div className="col-12 col-lg-6">
                    <div className="card shadow">
                      <div className="card-header py-3">
                        <h6 className="m-0">Account Information</h6>
                      </div>
                      <div className="card-body">
                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            Account Expiry
                          </span>
                          <input
                            type="text"
                            className="form-control"
                            id="basic-url"
                            aria-describedby="basic-addon3"
                            value={"2026-12-31 23:59:59"}
                            disabled
                          />
                        </div>

                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            Email
                          </span>
                          <input
                            type="text"
                            className="form-control"
                            id="basic-url"
                            aria-describedby="basic-addon3"
                            value={"user@example.com"}
                            disabled
                          />
                        </div>

                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            Username
                          </span>
                          <input
                            type="text"
                            className="form-control"
                            id="basic-url"
                            aria-describedby="basic-addon3"
                            value={"admin"}
                            disabled
                          />
                        </div>
                      </div>
                      <div className="card-footer py-3">
                        <h6 className="m-0">Profile details</h6>
                        <span className="text-muted small">
                          Expiry is managed by billing and is read-only.
                        </span>
                      </div>
                    </div>
                  </div>


<div className="col-12 col-lg-6">
                    <div className="card shadow">
                      <div className="card-header py-3">
                        <h6 className="m-0">Two-Step Authentication (OTP)</h6>
                      </div>
                      <div className="card-body">
                        <div className="form-check form-switch mb-2">
                          <input
                            className="form-check-input"
                            type="checkbox"
                            id="enable2fa"
                            checked={twoFAEnabled}
                            onChange={(e) => setTwoFAEnabled(e.target.checked)}
                          />
                          <label className="form-check-label" htmlFor="enable2fa">
                            Enable Two-Step Authentication
                          </label>
                        </div>
                        <div className="text-muted small mb-3">
                          Choose one or more delivery methods for OTP when 2FA is enabled.
                        </div>

                        <div className="form-check mb-2">
                          <input
                            className="form-check-input"
                            type="checkbox"
                            id="methodTelegram"
                            disabled={!twoFAEnabled}
                            checked={methods.telegram}
                            onChange={() => toggleMethod("telegram")}
                          />
                          <label className="form-check-label" htmlFor="methodTelegram">
                            Telegram OTP
                          </label>
                        </div>

                        <div className="form-check mb-3">
                          <input
                            className="form-check-input"
                            type="checkbox"
                            id="methodEmail"
                            disabled={!twoFAEnabled}
                            checked={methods.email}
                            onChange={() => toggleMethod("email")}
                          />
                          <label className="form-check-label" htmlFor="methodEmail">
                            Email OTP
                          </label>
                        </div>

                        <div className="d-grid">
                          <button
                            className="btn btn-primary"
                            type="button"
                            disabled={twoFAEnabled && !methods.telegram && !methods.email}
                          >
                            Save 2FA Settings
                          </button>
                        </div>
                      </div>
                      <div className="card-footer py-3">
                        {/* <h6 className="m-0">2FA Settings</h6> */}
                        <span className="text-muted small">
                          You must select at least one method when 2FA is enabled.
                        </span>
                      </div>
                    </div>
                  </div>




                  <div className="col-12 col-lg-6">
                    <div className="card shadow">
                      <div className="card-header py-3">
                        <h6 className="m-0">Advanced Security</h6>
                      </div>
                      <div className="card-body">
                        <h6 className="mb-2">Password Reset</h6>

                        <div className="input-group mb-2">
                          <label className="input-group-text" htmlFor="pwResetMethod">Verify With</label>
                          <select className="form-select" id="pwResetMethod">
                            <option value="current">Current Password</option>
                            <option value="telegram">Telegram OTP</option>
                            <option value="email">Email OTP</option>
                          </select>
                        </div>

                        <div className="input-group mb-2">
                          <span className="input-group-text">Current Password</span>
                          <input type="password" className="form-control" placeholder="Enter current password" />
                        </div>

                        <div className="input-group mb-2">
                          <span className="input-group-text">OTP</span>
                          <input type="text" className="form-control" placeholder="Enter OTP (Telegram/Email)" />
                          <button className="btn btn-outline-secondary" type="button">Send OTP</button>
                        </div>

                        <div className="input-group mb-2">
                          <span className="input-group-text">New Password</span>
                          <input type="password" className="form-control" placeholder="New password" />
                        </div>

                        <div className="input-group mb-3">
                          <span className="input-group-text">Confirm</span>
                          <input type="password" className="form-control" placeholder="Confirm new password" />
                        </div>

                        <div className="d-grid mb-3">
                          <button className="btn btn-primary" type="button">Update Password</button>
                        </div>
                      </div>
                      <div className="card-footer py-3">
                        <h6 className="m-0">Guide to manage account</h6>
                        <a
                          href="https://support.google.com/a/answer/176600?hl=en"
                          target="_blank"
                          rel="noreferrer"
                        >
                          Account Management documentation
                        </a>
                      </div>
                    </div>
                  </div>

                  
                </div>
              </main>
            </div>
          </main>
        </div>
      </div>
    </>
  );
};

export default AdminProfile;
