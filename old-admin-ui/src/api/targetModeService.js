import axios from 'axios';
import { API } from '../utils/constants.js';
import { getStoredToken, isValidToken } from '../utils/jwt.js';

const authApi = () => {
  const token = getStoredToken();
  const headers = { 'Content-Type': 'application/json' };
  if (token && isValidToken(token)) headers.Authorization = `Bearer ${token}`;
  return axios.create({ baseURL: API.BASE_URL, timeout: API.TIMEOUT, headers });
};

export const targetModeService = {
  async handshake(deviceId) {
    const api = authApi();
    const { data } = await api.post(`/device/${encodeURIComponent(deviceId)}/target-mode/handshake`);
    return data; // { endpoint, token, expiresAt }
  },
  async saveFocus(deviceId, payload) {
    const api = authApi();
    const { data } = await api.put(`/device/${encodeURIComponent(deviceId)}/target-mode/focus`, payload);
    return data; // { ok: true }
  },
  async loadFocus(deviceId) {
    const api = authApi();
    const { data } = await api.get(`/device/${encodeURIComponent(deviceId)}/target-mode/focus`);
    return data; // { packages:[], guard:true }
  },
};

export default targetModeService;
