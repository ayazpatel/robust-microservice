import React, { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { isAuthenticated, getCurrentUser } from '../utils/jwt.js';
import { AUTH } from '../utils/constants.js';

/**
 * ProtectedRoute Component
 * Wraps components that require authentication and/or specific roles
 */
const ProtectedRoute = ({ 
  children, 
  requiredRole = null, 
  redirectTo = '/login' 
}) => {
  const [isChecking, setIsChecking] = useState(true);
  const [hasAccess, setHasAccess] = useState(false);
  const location = useLocation();

  useEffect(() => {
    const checkAccess = async () => {
      try {
        // Check if user is authenticated
        if (!isAuthenticated()) {
          setHasAccess(false);
          setIsChecking(false);
          return;
        }

        // If no specific role required, just check authentication
        if (!requiredRole) {
          setHasAccess(true);
          setIsChecking(false);
          return;
        }

        // Check if user has required role
        const currentUser = getCurrentUser();
        if (!currentUser) {
          setHasAccess(false);
          setIsChecking(false);
          return;
        }

        const userHasRole = currentUser.role === requiredRole;
        setHasAccess(userHasRole);
        setIsChecking(false);
      } catch (error) {
        console.error('Access check error:', error);
        setHasAccess(false);
        setIsChecking(false);
      }
    };

    checkAccess();
  }, [requiredRole]);

  // Show loading spinner while checking access
  if (isChecking) {
    return (
      <div className="d-flex justify-content-center align-items-center min-vh-100">
        <div className="text-center">
          <div className="spinner-border text-primary mb-3" role="status">
            <span className="visually-hidden">Loading...</span>
          </div>
          <p className="text-muted">Verifying access...</p>
        </div>
      </div>
    );
  }

  // Redirect if no access
  if (!hasAccess) {
    return (
      <Navigate 
        to={redirectTo} 
        state={{ from: location }} 
        replace 
      />
    );
  }

  // Render protected content
  return children;
};

/**
 * AdminRoute Component
 * Specifically for admin-only routes
 */
export const AdminRoute = ({ children, redirectTo = '/login' }) => {
  return (
    <ProtectedRoute requiredRole={AUTH.ROLES.ADMIN} redirectTo={redirectTo}>
      {children}
    </ProtectedRoute>
  );
};

/**
 * OwnerRoute Component
 * Specifically for owner-only routes
 */
export const OwnerRoute = ({ children, redirectTo = '/login' }) => {
  return (
    <ProtectedRoute requiredRole={AUTH.ROLES.OWNER} redirectTo={redirectTo}>
      {children}
    </ProtectedRoute>
  );
};

/**
 * AuthRoute Component
 * For any authenticated user (any role)
 */
export const AuthRoute = ({ children, redirectTo = '/login' }) => {
  return (
    <ProtectedRoute redirectTo={redirectTo}>
      {children}
    </ProtectedRoute>
  );
};

/**
 * GuestRoute Component
 * For unauthenticated users only (redirects authenticated users to their dashboard)
 */
export const GuestRoute = ({ children }) => {
  const [isChecking, setIsChecking] = useState(true);
  const [isAuth, setIsAuth] = useState(false);
  const [dashboardRoute, setDashboardRoute] = useState('/');

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      const authenticated = isAuthenticated();
      setIsAuth(authenticated);

      if (authenticated) {
        const currentUser = getCurrentUser();
        if (currentUser) {
          const route = currentUser.role === AUTH.ROLES.ADMIN 
            ? '/admin/dashboard' 
            : '/owner/dashboard';
          setDashboardRoute(route);
        }
      }
      
      setIsChecking(false);
    } catch (error) {
      console.error('Auth status check error:', error);
      setIsAuth(false);
      setIsChecking(false);
    }
  };

  // Show loading while checking
  if (isChecking) {
    return (
      <div className="d-flex justify-content-center align-items-center min-vh-100">
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  // Redirect authenticated users to their dashboard
  if (isAuth) {
    return <Navigate to={dashboardRoute} replace />;
  }

  // Show content for unauthenticated users
  return children;
};

export default ProtectedRoute;