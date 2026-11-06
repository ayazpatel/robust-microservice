import axios from 'axios';
import { API, AUTH, HTTP_STATUS } from '../utils/constants.js';
import { getStoredToken, isValidToken, removeToken } from '../utils/jwt.js';

/**
 * Authentication Service
 * Handles all authentication-related API calls and token management
 */

// Create axios instance with default config
const authApi = axios.create({
  baseURL: API.BASE_URL,
  timeout: API.TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add auth token
authApi.interceptors.request.use(
  (config) => {
    const token = getStoredToken();
    if (token && isValidToken(token)) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor to handle auth errors
authApi.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === HTTP_STATUS.UNAUTHORIZED) {
      // Token expired or invalid, remove it and redirect to login
      removeToken();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

/**
 * Login user with username and password
 * @param {object} credentials - Login credentials
 * @param {string} credentials.username - Username
 * @param {string} credentials.password - Password
 * @returns {Promise<object>} - Login response with token
 */
export const login = async (credentials) => {
  try {
    const response = await authApi.post(`${API.ENDPOINTS.AUTH}/login`, credentials);
    
    if (response.data.success && response.data.data?.token) {
      return {
        success: true,
        token: response.data.data.token,
        message: response.data.message || 'Login successful',
      };
    } else {
      return {
        success: false,
        error: 'INVALID_RESPONSE',
        message: 'Invalid response from server',
      };
    }
  } catch (error) {
    console.error('Login error:', error);
    
    if (error.response?.data) {
      return {
        success: false,
        error: error.response.data.error || 'LOGIN_FAILED',
        message: error.response.data.message || 'Login failed',
      };
    }
    
    return {
      success: false,
      error: 'NETWORK_ERROR',
      message: 'Network error. Please check your connection.',
    };
  }
};

/**
 * Logout user (client-side token removal)
 * @returns {Promise<object>} - Logout result
 */
export const logout = async () => {
  try {
    removeToken();
    return {
      success: true,
      message: 'Logged out successfully',
    };
  } catch (error) {
    console.error('Logout error:', error);
    return {
      success: false,
      error: 'LOGOUT_ERROR',
      message: 'Error during logout',
    };
  }
};

/**
 * Verify current token validity
 * @returns {Promise<object>} - Token verification result
 */
export const verifyToken = async () => {
  try {
    const token = getStoredToken();
    
    if (!token || !isValidToken(token)) {
      return {
        success: false,
        error: 'INVALID_TOKEN',
        message: 'No valid token found',
      };
    }
    
    // You could add a backend endpoint to verify token if needed
    // For now, we'll rely on client-side validation
    return {
      success: true,
      message: 'Token is valid',
    };
  } catch (error) {
    console.error('Token verification error:', error);
    return {
      success: false,
      error: 'VERIFICATION_ERROR',
      message: 'Error verifying token',
    };
  }
};

/**
 * Refresh token (if your backend supports it)
 * Note: Currently your backend doesn't seem to have a refresh endpoint
 * This is a placeholder for future implementation
 */
export const refreshToken = async () => {
  try {
    // Placeholder for refresh token implementation
    const token = getStoredToken();
    
    if (!token) {
      return {
        success: false,
        error: 'NO_TOKEN',
        message: 'No token to refresh',
      };
    }
    
    // If you implement refresh token endpoint in backend:
    // const response = await authApi.post(`${API.ENDPOINTS.AUTH}/refresh`, { token });
    
    return {
      success: false,
      error: 'NOT_IMPLEMENTED',
      message: 'Token refresh not implemented',
    };
  } catch (error) {
    console.error('Token refresh error:', error);
    return {
      success: false,
      error: 'REFRESH_ERROR',
      message: 'Error refreshing token',
    };
  }
};

/**
 * Check if user has permission for specific role
 * @param {string} requiredRole - Required role (ADMIN/OWNER)
 * @returns {Promise<object>} - Permission check result
 */
export const checkPermission = async (requiredRole) => {
  try {
    const verification = await verifyToken();
    
    if (!verification.success) {
      return verification;
    }
    
    const token = getStoredToken();
    const decoded = token ? JSON.parse(atob(token.split('.')[1])) : null;
    
    if (!decoded || decoded.role !== requiredRole) {
      return {
        success: false,
        error: 'INSUFFICIENT_PERMISSIONS',
        message: 'Insufficient permissions for this action',
      };
    }
    
    return {
      success: true,
      message: 'Permission granted',
    };
  } catch (error) {
    console.error('Permission check error:', error);
    return {
      success: false,
      error: 'PERMISSION_ERROR',
      message: 'Error checking permissions',
    };
  }
};

/**
 * Get user profile information
 * This would typically call a backend endpoint to get user details
 * For now, it extracts info from the stored token
 */
export const getUserProfile = async () => {
  try {
    const verification = await verifyToken();
    
    if (!verification.success) {
      return verification;
    }
    
    const token = getStoredToken();
    const decoded = token ? JSON.parse(atob(token.split('.')[1])) : null;
    
    if (!decoded) {
      return {
        success: false,
        error: 'INVALID_TOKEN',
        message: 'Could not decode user information',
      };
    }
    
    return {
      success: true,
      data: {
        id: decoded.userId,
        username: decoded.username,
        role: decoded.role,
        subject: decoded.sub,
        issuedAt: new Date(decoded.iat * 1000).toISOString(),
        expiresAt: new Date(decoded.exp * 1000).toISOString(),
      },
      message: 'User profile retrieved successfully',
    };
  } catch (error) {
    console.error('Get user profile error:', error);
    return {
      success: false,
      error: 'PROFILE_ERROR',
      message: 'Error retrieving user profile',
    };
  }
};

// Export the configured axios instance for other API calls
export { authApi };

// Default export with all auth functions
export default {
  login,
  logout,
  verifyToken,
  refreshToken,
  checkPermission,
  getUserProfile,
  authApi,
};