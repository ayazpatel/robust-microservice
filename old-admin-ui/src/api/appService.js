import axios from 'axios';
import { API, HTTP_STATUS } from '../utils/constants.js';
import { getStoredToken, isValidToken, removeToken } from '../utils/jwt.js';

const withAuth = () => {
  const token = getStoredToken();
  const headers = { 'Content-Type': 'application/json' };
  if (token && isValidToken(token)) headers.Authorization = `Bearer ${token}`;
  return axios.create({baseURL: API.BASE_URL, timeout: API.TIMEOUT, headers });
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

export const appService = {
  async myApps(includeInactive = false) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.get(`/admin/apps`, { params: { includeInactive } });
      return data; // expect array or { apps, ... }
    });
  },

  async getAppJson(appId) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.get(`/admin/apps/${encodeURIComponent(appId)}/json`);
      return data; // full app json
    });
  },

  async getSettings(appId) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.get(`/admin/apps/${encodeURIComponent(appId)}/settings`);
      return data;
    });
  },

  async updateManagement(appId, payload) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.put(`/admin/apps/${encodeURIComponent(appId)}/settings/management`, payload);
      return data;
    });
  },

  async validateKey(appId) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.post(`/admin/apps/${encodeURIComponent(appId)}/validate-key`);
      return data; // { message }
    });
  },

  // SMS APIs
  async listSms(params) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.get(`/sms/all`, { params });
      return data; // expect array or { items }
    });
  },

  async sendSms({ to, message, type, priority }) {
    return handle(async () => {
      const api = withAuth();
      const { data } = await api.post(`/sms/send`, { to, message, type, priority });
      return data; // { messageId, status, ... }
    });
  },
};

export default appService;
