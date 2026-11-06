import { jwtDecode } from 'jwt-decode';
import { AUTH } from './constants.js';

/**
 * JWT Utility Functions
 * Handles token validation, decoding, and management
 */

/**
 * Decode JWT token and extract payload
 * @param {string} token - JWT token to decode
 * @returns {object|null} - Decoded payload or null if invalid
 */
export const decodeToken = (token) => {
  try {
    if (!token) return null;
    return jwtDecode(token);
  } catch (error) {
    console.error('Error decoding token:', error);
    return null;
  }
};

/**
 * Check if token is expired
 * @param {string} token - JWT token to check
 * @returns {boolean} - True if expired, false otherwise
 */
export const isTokenExpired = (token) => {
  try {
    const decoded = decodeToken(token);
    if (!decoded || !decoded.exp) return true;
    
    const currentTime = Date.now() / 1000;
    return decoded.exp < currentTime;
  } catch (error) {
    console.error('Error checking token expiration:', error);
    return true;
  }
};

/**
 * Validate token structure and content
 * @param {string} token - JWT token to validate
 * @returns {boolean} - True if valid, false otherwise
 */
export const isValidToken = (token) => {
  try {
    if (!token) return false;
    
    const decoded = decodeToken(token);
    if (!decoded) return false;
    
    // Check required fields based on your JWT structure
    const hasRequiredFields = 
      decoded.sub &&           // Username (standard claim)
      decoded.username &&      // Username (custom claim)
      decoded.userId &&        // User ID (MongoDB ObjectId)
      decoded.role &&          // Role (ADMIN/OWNER)
      decoded.exp &&           // Expiration
      decoded.iat;             // Issued at
    
    if (!hasRequiredFields) return false;
    
    // Check if token is not expired
    return !isTokenExpired(token);
  } catch (error) {
    console.error('Error validating token:', error);
    return false;
  }
};

/**
 * Get user data from token
 * @param {string} token - JWT token
 * @returns {object|null} - User data or null if invalid
 */
export const getUserFromToken = (token) => {
  try {
    if (!isValidToken(token)) return null;
    
    const decoded = decodeToken(token);
    return {
      id: decoded.userId,
      username: decoded.username,
      role: decoded.role,
      subject: decoded.sub,
      issuedAt: decoded.iat,
      expiresAt: decoded.exp,
    };
  } catch (error) {
    console.error('Error extracting user from token:', error);
    return null;
  }
};

/**
 * Get user role from token
 * @param {string} token - JWT token
 * @returns {string|null} - User role or null if invalid
 */
export const getRoleFromToken = (token) => {
  try {
    const user = getUserFromToken(token);
    return user ? user.role : null;
  } catch (error) {
    console.error('Error extracting role from token:', error);
    return null;
  }
};

/**
 * Check if user has specific role
 * @param {string} token - JWT token
 * @param {string} role - Role to check (ADMIN/OWNER)
 * @returns {boolean} - True if user has role, false otherwise
 */
export const hasRole = (token, role) => {
  try {
    const userRole = getRoleFromToken(token);
    return userRole === role;
  } catch (error) {
    console.error('Error checking user role:', error);
    return false;
  }
};

/**
 * Get token from localStorage
 * @returns {string|null} - Token or null if not found
 */
export const getStoredToken = () => {
  try {
    return localStorage.getItem(AUTH.TOKEN_KEY);
  } catch (error) {
    console.error('Error getting stored token:', error);
    return null;
  }
};

/**
 * Store token in localStorage
 * @param {string} token - JWT token to store
 */
export const storeToken = (token) => {
  try {
    if (token) {
      localStorage.setItem(AUTH.TOKEN_KEY, token);
    }
  } catch (error) {
    console.error('Error storing token:', error);
  }
};

/**
 * Remove token from localStorage
 */
export const removeToken = () => {
  try {
    localStorage.removeItem(AUTH.TOKEN_KEY);
    localStorage.removeItem(AUTH.USER_KEY);
  } catch (error) {
    console.error('Error removing token:', error);
  }
};

/**
 * Get current authenticated user data
 * @returns {object|null} - User data or null if not authenticated
 */
export const getCurrentUser = () => {
  try {
    const token = getStoredToken();
    return getUserFromToken(token);
  } catch (error) {
    console.error('Error getting current user:', error);
    return null;
  }
};

/**
 * Check if user is currently authenticated
 * @returns {boolean} - True if authenticated, false otherwise
 */
export const isAuthenticated = () => {
  try {
    const token = getStoredToken();
    return isValidToken(token);
  } catch (error) {
    console.error('Error checking authentication:', error);
    return false;
  }
};

/**
 * Get dashboard route based on user role
 * @param {string} token - JWT token
 * @returns {string} - Dashboard route for the user's role
 */
export const getDashboardRoute = (token) => {
  try {
    const role = getRoleFromToken(token);
    
    switch (role) {
      case AUTH.ROLES.ADMIN:
        return '/admin/dashboard';
      case AUTH.ROLES.OWNER:
        return '/owner/dashboard';
      default:
        return '/login';
    }
  } catch (error) {
    console.error('Error getting dashboard route:', error);
    return '/login';
  }
};

/**
 * Format token expiration time for display
 * @param {string} token - JWT token
 * @returns {string} - Formatted expiration time
 */
export const getTokenExpirationTime = (token) => {
  try {
    const decoded = decodeToken(token);
    if (!decoded || !decoded.exp) return 'Unknown';
    
    const expirationDate = new Date(decoded.exp * 1000);
    return expirationDate.toLocaleString();
  } catch (error) {
    console.error('Error getting token expiration time:', error);
    return 'Unknown';
  }
};

/**
 * Get time remaining until token expires
 * @param {string} token - JWT token
 * @returns {number} - Minutes remaining or 0 if expired
 */
export const getTokenTimeRemaining = (token) => {
  try {
    const decoded = decodeToken(token);
    if (!decoded || !decoded.exp) return 0;
    
    const currentTime = Date.now() / 1000;
    const timeRemaining = decoded.exp - currentTime;
    
    return timeRemaining > 0 ? Math.floor(timeRemaining) : 0; // Return seconds
  } catch (error) {
    console.error('Error getting token time remaining:', error);
    return 0;
  }
};

/**
 * Format seconds as HH:MM:SS
 */
export const formatHMS = (totalSeconds) => {
  if (totalSeconds == null || Number.isNaN(totalSeconds)) return 'N/A';
  const secs = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  const s = secs % 60;
  const pad = (n) => n.toString().padStart(2, '0');
  return `${pad(h)}:${pad(m)}:${pad(s)}`;
};

export default {
  decodeToken,
  isTokenExpired,
  isValidToken,
  getUserFromToken,
  getRoleFromToken,
  hasRole,
  getStoredToken,
  storeToken,
  removeToken,
  getCurrentUser,
  isAuthenticated,
  getDashboardRoute,
  getTokenExpirationTime,
  getTokenTimeRemaining,
  formatHMS,
};