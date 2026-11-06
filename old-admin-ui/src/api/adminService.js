import axios from 'axios';
import { API, HTTP_STATUS } from '../utils/constants.js';
import { getStoredToken, isValidToken, removeToken } from '../utils/jwt.js';

const base = API.ENDPOINTS.ADMIN;

const withAuth = () => {
  const token = getStoredToken();
  const headers = { 'Content-Type': 'application/json' };
  if (token && isValidToken(token)) headers.Authorization = `Bearer ${token}`;
  return axios.create({ baseURL: API.BASE_URL, timeout: API.TIMEOUT, headers });
};

const handle = async (fn) => {
  try {
    return await fn();
  } catch (error) {
    if (error.response?.status === HTTP_STATUS.UNAUTHORIZED) {
      removeToken();
      window.location.href = '/login';
    }
    throw error;
  }
};

export const adminService = {
  async me() {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.get(`${base}/me`);
      return data; // profile map
    });
  },
  async getAll() {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.get(`${base}/all`);
      return data; // { admins, total }
    });
  },

  async getStats() {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.get(`${base}/stats`);
      return data;
    });
  },

  // Updated: atomic create (admin + app). Owner-only endpoint.
  // Payload: { username, password, expiryInDays, categoryId }
  // Response: { message, admin: {...}, app: {...} }
  async create({ username, password, expiryInDays, categoryId }) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.post(`${API.ENDPOINTS.OWNER}/admins/create`, {
        username,
        password,
        expiryInDays,
        categoryId,
      });
      return data;
    });
  },

  async updatePassword(adminId, newPassword) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.put(`${base}/${encodeURIComponent(adminId)}/password`, { newPassword });
      return data;
    });
  },

  async updateExpiry(adminId, expiryInDays) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.put(`${base}/${encodeURIComponent(adminId)}/expiry`, { expiryInDays });
      return data; // { message, expiry }
    });
  },

  async remove(adminId) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.delete(`${base}/${encodeURIComponent(adminId)}`);
      return data; // { message }
    });
  },

  // Owner-only: manage categories assigned to an admin
  async assignCategories(adminId, categoryIds, mode = 'add') {
    return handle(async () => {
      const api = withAuth();
      const ownerBase = `${API.ENDPOINTS.OWNER}/admins/${encodeURIComponent(adminId)}/categories`;
      const { data } = await api.put(ownerBase, { categoryIds, mode });
      return data; // { message, adminId, categories }
    });
  },
  async getAssignedCategories(adminId) {
    return handle(async () => {
      const api = withAuth();
      const ownerBase = `${API.ENDPOINTS.OWNER}/admins/${encodeURIComponent(adminId)}/categories`;
      const { data } = await api.get(ownerBase);
      return data; // { categories: [...] }
    });
  },
};

export default adminService;
