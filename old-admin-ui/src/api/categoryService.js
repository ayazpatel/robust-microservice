import axios from 'axios';
import { API, HTTP_STATUS } from '../utils/constants.js';
import { getStoredToken, isValidToken, removeToken } from '../utils/jwt.js';

const base = `${API.ENDPOINTS.OWNER}/categories`;

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

export const categoryService = {
  async list({ includeInactive = false } = {}) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.get(`${base}?includeInactive=${includeInactive}`);
      // data: { categories, stats }
      return data;
    });
  },
  async listWithCounts() {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.get(`${base}/with-app-counts`);
      return data; // { categoriesWithCounts }
    });
  },
  async getById(categoryId) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.get(`${base}/${encodeURIComponent(categoryId)}`);
      return data; // category
    });
  },
  async create({ title, blog, downloadLink }) {
    return handle(async () => {
      const api = withAuth();
      const { data, status } = await api.post(`${base}`, { title, blog, downloadLink });
      return { data, status };
    });
  },
  async update(categoryId, { title, blog, downloadLink }) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.put(`${base}/${encodeURIComponent(categoryId)}`, { title, blog, downloadLink });
      return data; // { message, category }
    });
  },
  async toggleStatus(categoryId, active) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.put(`${base}/${encodeURIComponent(categoryId)}/status`, { active });
      return data; // { message, category }
    });
  },
  async remove(categoryId) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.delete(`${base}/${encodeURIComponent(categoryId)}`);
      return data; // { message }
    });
  },
  // Assignment APIs - require backend support. Proposed endpoint:
  // PUT /api/owner/categories/{categoryId}/assign  body: { adminIds: string[], mode?: 'replace'|'add'|'remove' }
  async assignToAdmins(categoryId, adminIds, mode = 'add') {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.put(`${base}/${encodeURIComponent(categoryId)}/assign`, { adminIds, mode });
      return data; // { message, categoryId, affectedAdminCount }
    });
  },
};

export default categoryService;
