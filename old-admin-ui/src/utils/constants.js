// Environment Variables
export const ENV = {
  APP_NAME: import.meta.env.VITE_APP_NAME || 'Admin Dashboard',
  APP_VERSION: import.meta.env.VITE_APP_VERSION || '1.0.0',
  NODE_ENV: import.meta.env.VITE_NODE_ENV || 'development',
};

// API Configuration
export const API = {
  BASE_URL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  TIMEOUT: parseInt(import.meta.env.VITE_API_TIMEOUT) || 10000,
  ENDPOINTS: {
    AUTH: '/auth',
    ADMIN: '/admin',
    OWNER: '/owner',
    PUBLIC_MOBILE: '/public-mobile',
    SMS: '/sms',
  },
};

// Authentication Constants
export const AUTH = {
  JWT_SECRET: import.meta.env.VITE_JWT_SECRET_KEY,
  TOKEN_EXPIRY: 24 * 60 * 60, // 1 day in seconds
  TOKEN_KEY: 'admin_token',
  USER_KEY: 'admin_user',
  ROLES: {
    ADMIN: 'ADMIN',
    OWNER: 'OWNER',
  },
};

// Third-party Service Keys
export const SERVICES = {
  GOOGLE_CLIENT_ID: import.meta.env.VITE_GOOGLE_CLIENT_ID,
  FIREBASE_API_KEY: import.meta.env.VITE_FIREBASE_API_KEY,
};

// Feature Flags
export const FEATURES = {
  ANALYTICS_ENABLED: import.meta.env.VITE_ENABLE_ANALYTICS === 'true',
  DEBUG_ENABLED: import.meta.env.VITE_ENABLE_DEBUG === 'true',
};

// UI Constants
export const UI = {
  DEFAULT_THEME: import.meta.env.VITE_DEFAULT_THEME || 'light',
  ITEMS_PER_PAGE: parseInt(import.meta.env.VITE_ITEMS_PER_PAGE) || 10,
  THEMES: ['light', 'dark'],
  BREAKPOINTS: {
    XS: 0,
    SM: 576,
    MD: 768,
    LG: 992,
    XL: 1200,
    XXL: 1400,
  },
};

// Application Constants
export const APP_CONSTANTS = {
  ROLES: {
    ADMIN: 'ADMIN',
    OWNER: 'OWNER',
  },
  STATUS: {
    ACTIVE: 'active',
    INACTIVE: 'inactive',
    PENDING: 'pending',
    SUSPENDED: 'suspended',
  },
  NOTIFICATION_TYPES: {
    SUCCESS: 'success',
    ERROR: 'error',
    WARNING: 'warning',
    INFO: 'info',
  },
  ROUTES: {
    LOGIN: '/login',
    ADMIN_DASHBOARD: '/admin/dashboard',
    OWNER_DASHBOARD: '/owner/dashboard',
  },
};

// HTTP Status Codes
export const HTTP_STATUS = {
  OK: 200,
  CREATED: 201,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  INTERNAL_SERVER_ERROR: 500,
};

// Date/Time Formats
export const DATE_FORMATS = {
  DISPLAY: 'MM/DD/YYYY',
  API: 'YYYY-MM-DD',
  TIMESTAMP: 'YYYY-MM-DD HH:mm:ss',
  TIME_ONLY: 'HH:mm:ss',
};

// Validation Rules
export const VALIDATION = {
  PASSWORD_MIN_LENGTH: 8,
  USERNAME_MIN_LENGTH: 3,
  EMAIL_REGEX: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
  PHONE_REGEX: /^\+?[\d\s-()]+$/,
};

// Default export with all constants
const constants = {
  ENV,
  API,
  AUTH,
  SERVICES,
  FEATURES,
  UI,
  APP_CONSTANTS,
  HTTP_STATUS,
  DATE_FORMATS,
  VALIDATION,
};

export default constants;