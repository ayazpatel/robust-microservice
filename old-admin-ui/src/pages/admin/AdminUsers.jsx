import React from 'react';

const AdminUsers = () => {
  return (
    <div>
      <h2 className="h4 mb-3">User Management</h2>
      {/* <div className="alert alert-info">This is a placeholder page for managing users.</div> */}
      {(() => {
        const users = [
          { id: 1, name: 'Alice Johnson', email: 'alice@example.com', role: 'Admin', status: 'Active', joined: '2024-06-01' },
          { id: 2, name: 'Bob Smith', email: 'bob@example.com', role: 'User', status: 'Active', joined: '2024-06-12' },
          { id: 3, name: 'Carol Lee', email: 'carol@example.com', role: 'Manager', status: 'Suspended', joined: '2024-05-27' },
          { id: 4, name: 'David Kim', email: 'david@example.com', role: 'User', status: 'Active', joined: '2024-04-18' },
          { id: 5, name: 'Eve Turner', email: 'eve@example.com', role: 'User', status: 'Active', joined: '2024-07-02' },
          { id: 6, name: 'Frank Miller', email: 'frank@example.com', role: 'Manager', status: 'Active', joined: '2024-02-14' },
          { id: 7, name: 'Grace Hopper', email: 'grace@example.com', role: 'Admin', status: 'Active', joined: '2024-03-11' },
          { id: 8, name: 'Henry Ford', email: 'henry@example.com', role: 'User', status: 'Suspended', joined: '2024-01-25' },
        ];
        const roleBadge = (role) =>
          role === 'Admin' ? 'bg-danger' : role === 'Manager' ? 'bg-primary' : 'bg-secondary';
        const statusBadge = (status) =>
          status === 'Active' ? 'bg-success' : 'bg-warning text-dark';

        return (
          <>
            <div className="d-flex justify-content-between align-items-center mb-3">
              <ul className="nav nav-pills" id="viewTabs" role="tablist">
                <li className="nav-item" role="presentation">
                  <button className="nav-link active" id="grid-tab" data-bs-toggle="tab" data-bs-target="#grid-pane" type="button" role="tab" aria-controls="grid-pane" aria-selected="true">
                    Grid
                  </button>
                </li>
                <li className="nav-item" role="presentation">
                  <button className="nav-link" id="table-tab" data-bs-toggle="tab" data-bs-target="#table-pane" type="button" role="tab" aria-controls="table-pane" aria-selected="false">
                    Table
                  </button>
                </li>
              </ul>
              <div>
                <a href="/admin/users/new" className="btn btn-sm btn-primary">Add User</a>
              </div>
            </div>

            <div className="tab-content">
              <div className="tab-pane fade show active" id="grid-pane" role="tabpanel" aria-labelledby="grid-tab" tabIndex="0">
                <div className="row row-cols-1 row-cols-sm-2 row-cols-md-3 row-cols-lg-4 g-3">
                  {users.map((u) => (
                    <div className="col" key={u.id}>
                      <div className="card h-100 shadow-sm">
                        <div className="card-body">
                          <div className="d-flex align-items-center mb-2">
                            <div className="rounded-circle bg-light border me-3 d-flex align-items-center justify-content-center" style={{ width: 48, height: 48 }}>
                              <span className="fw-semibold text-secondary">{u.name.split(' ').map(n => n[0]).slice(0,2).join('')}</span>
                            </div>
                            <div className="flex-grow-1">
                              <h5 className="card-title mb-0">{u.name}</h5>
                              <small className="text-muted">{u.email}</small>
                            </div>
                          </div>
                          <div className="mb-2">
                            <span className={`badge me-1 ${roleBadge(u.role)}`}>{u.role}</span>
                            <span className={`badge ${statusBadge(u.status)}`}>{u.status}</span>
                          </div>
                          <small className="text-muted">Joined {u.joined}</small>
                        </div>
                        <div className="card-footer bg-transparent border-0 pt-0">
                          <a href={`/admin/users/${u.id}`} className="stretched-link btn btn-sm btn-outline-secondary">View details</a>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="tab-pane fade" id="table-pane" role="tabpanel" aria-labelledby="table-tab" tabIndex="0">
                <div className="table-responsive">
                  <table className="table align-middle table-hover">
                    <thead className="table-light">
                      <tr>
                        <th scope="col">User</th>
                        <th scope="col">Email</th>
                        <th scope="col">Role</th>
                        <th scope="col">Status</th>
                        <th scope="col" className="text-end">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {users.map((u) => (
                        <tr key={u.id}>
                          <td>
                            <div className="d-flex align-items-center">
                              <div className="rounded-circle bg-light border me-3 d-flex align-items-center justify-content-center" style={{ width: 32, height: 32 }}>
                                <span className="small text-secondary fw-semibold">{u.name.split(' ').map(n => n[0]).slice(0,2).join('')}</span>
                              </div>
                              <div>
                                <div className="fw-semibold">{u.name}</div>
                                <small className="text-muted">Joined {u.joined}</small>
                              </div>
                            </div>
                          </td>
                          <td>{u.email}</td>
                          <td><span className={`badge ${roleBadge(u.role)}`}>{u.role}</span></td>
                          <td><span className={`badge ${statusBadge(u.status)}`}>{u.status}</span></td>
                          <td className="text-end">
                            <div className="btn-group btn-group-sm" role="group">
                              <a href={`/admin/users/${u.id}`} className="btn btn-outline-secondary">View</a>
                              <a href={`/admin/users/${u.id}/edit`} className="btn btn-outline-primary">Edit</a>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </>
        );
      })()}
    </div>
  );
};

export default AdminUsers;
