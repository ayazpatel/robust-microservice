import React from "react";

const AdminSupport = () => {
  const tickets = [
    {
      id: "TCK-1001",
      subject: "Can’t log in",
      status: "Open",
      priority: "High",
      createdAt: "2025-09-18 14:20",
      updatedAt: "2025-09-19 08:10",
      userId: "U-101",
      username: "alice",
    },
    {
      id: "TCK-1002",
      subject: "Billing question",
      status: "Pending",
      priority: "Medium",
      createdAt: "2025-09-17 09:05",
      updatedAt: "2025-09-18 16:40",
      userId: "U-202",
      username: "bob",
    },
    {
      id: "TCK-1003",
      subject: "Feature request",
      status: "Closed",
      priority: "Low",
      createdAt: "2025-09-10 11:33",
      updatedAt: "2025-09-15 12:00",
      userId: "U-303",
      username: "carol",
    },
  ];

  const getStatusBadge = (status) => {
    switch (status) {
      case "Open":
        return "badge bg-success";
      case "Pending":
        return "badge bg-warning text-dark";
      case "Closed":
        return "badge bg-secondary";
      default:
        return "badge bg-light text-dark";
    }
  };

  const getPriorityBadge = (priority) => {
    switch (priority) {
      case "High":
        return "badge bg-danger";
      case "Medium":
        return "badge bg-primary";
      case "Low":
        return "badge bg-info text-dark";
      default:
        return "badge bg-light text-dark";
    }
  };

  return (
    <>
      <div className="min-vh-100 bg-light">
        <div className="container-fluid">
          <div className="row">
            <main className="col-12 col-lg-12 px-2 px-sm-3 px-md-4 w-100">
              <div className="d-flex flex-wrap justify-content-between align-items-center pt-3 pb-2 mb-3 border-bottom">
                <h1 className="h4 h3-md mb-0">Customer Help & Support</h1>
              </div>
              {/* Tab Navigation */}
              <div className="container mt-4">
                <div className="card">
                  <div className="card-header">
                    <ul
                      className="nav nav-tabs card-header-tabs"
                      id="supportTabs"
                      role="tablist"
                    >
                      <li className="nav-item" role="presentation">
                        <button
                          className="nav-link active"
                          id="create-ticket-tab"
                          data-bs-toggle="tab"
                          data-bs-target="#create-ticket"
                          type="button"
                          role="tab"
                          aria-controls="create-ticket"
                          aria-selected="true"
                        >
                          Create Ticket
                        </button>
                      </li>
                      <li className="nav-item" role="presentation">
                        <button
                          className="nav-link"
                          id="show-tickets-tab"
                          data-bs-toggle="tab"
                          data-bs-target="#show-tickets"
                          type="button"
                          role="tab"
                          aria-controls="show-tickets"
                          aria-selected="false"
                        >
                          Show Tickets
                        </button>
                      </li>
                    </ul>
                  </div>
                  <div className="card-body">
                    <div className="tab-content" id="supportTabsContent">
                      {/* Create Ticket Tab */}
                      <div
                        className="tab-pane fade show active"
                        id="create-ticket"
                        role="tabpanel"
                        aria-labelledby="create-ticket-tab"
                      >
                        <h5 className="card-title">
                          Submit a New Support Ticket
                        </h5>
                        <form>
                          <div className="mb-3">
                            <label htmlFor="userId" className="form-label">
                              User ID
                            </label>
                            <input
                              type="text"
                              className="form-control"
                              id="userId"
                              value="[Pre-filled User ID]"
                              readOnly
                            />
                          </div>
                          <div className="mb-3">
                            <label htmlFor="username" className="form-label">
                              Username
                            </label>
                            <input
                              type="text"
                              className="form-control"
                              id="username"
                              value="[Pre-filled Username]"
                              readOnly
                            />
                          </div>
                          <div className="mb-3">
                            <label htmlFor="subject" className="form-label">
                              Subject
                            </label>
                            <input
                              type="text"
                              className="form-control"
                              id="subject"
                              placeholder="Enter subject"
                              required
                            />
                          </div>
                          <div className="mb-3">
                            <label htmlFor="message" className="form-label">
                              Message
                            </label>
                            <textarea
                              className="form-control"
                              id="message"
                              placeholder="Enter message"
                              required
                            ></textarea>
                          </div>
                          <button type="submit" className="btn btn-primary">
                            Submit Ticket
                          </button>
                        </form>
                      </div>

                      {/* Show Tickets Tab */}
                      <div
                        className="tab-pane fade"
                        id="show-tickets"
                        role="tabpanel"
                        aria-labelledby="show-tickets-tab"
                      >
                        <h5 className="card-title">All Tickets</h5>
                        {tickets.length === 0 ? (
                          <div className="text-muted">No tickets yet.</div>
                        ) : (
                          <div className="row g-3">
                            {tickets.map((t) => (
                              <div key={t.id} className="col-12 col-md-6 col-xl-4">
                                <div className="card h-100 shadow-sm">
                                  <div className="card-body">
                                    <div className="d-flex justify-content-between align-items-start">
                                      <h6 className="card-title mb-1">{t.subject}</h6>
                                      <span className={getStatusBadge(t.status)}>
                                        {t.status}
                                      </span>
                                    </div>
                                    <p className="mb-2 text-muted small">
                                      #{t.id} • {t.username} ({t.userId})
                                    </p>
                                    <div className="d-flex align-items-center gap-2">
                                      <span className={getPriorityBadge(t.priority)}>
                                        {t.priority}
                                      </span>
                                      <span className="small text-muted ms-auto">
                                        Opened {t.createdAt}
                                      </span>
                                    </div>
                                  </div>
                                  <div className="card-footer d-flex justify-content-between align-items-center">
                                    <small className="text-muted">
                                      Updated {t.updatedAt}
                                    </small>
                                    <div className="btn-group btn-group-sm">
                                      <button type="button" className="btn btn-outline-primary">
                                        View
                                      </button>
                                      <button type="button" className="btn btn-outline-secondary">
                                        Assign
                                      </button>
                                    </div>
                                  </div>
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </main>
          </div>
        </div>
      </div>
    </>
  );
};

export default AdminSupport;
