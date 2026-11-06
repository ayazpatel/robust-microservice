import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { appService } from '../../api/appService.js';
import { targetModeService } from '../../api/targetModeService.js';
import { adminService } from '../../api/adminService.js';

// Unified state for admin session: { admin, app } + live appJson and selected deviceId
const initialState = {
  admin: null, // from /admin/me or auth.profile
  app: null,   // first app for this admin (or selected app)
  appJson: null,
  deviceId: '',
  loading: false,
  error: null,
  // Target Mode persistent state
  targetMode: {
    focusedPackages: [],
    uninstallGuard: true,
    ws: {
      connecting: false,
      connected: false,
      error: null,
      endpoint: null,
      token: null,
    },
  },
};

export const loadAdminMe = createAsyncThunk('adminApp/loadAdminMe', async (_, { rejectWithValue }) => {
  try {
    const res = await adminService.me();
    return res?.data || res; // tolerate either shape
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to load admin profile');
  }
});

export const loadMyPrimaryApp = createAsyncThunk('adminApp/loadMyPrimaryApp', async (_, { rejectWithValue }) => {
  try {
    const res = await appService.myApps(false);
    const apps = Array.isArray(res?.apps) ? res.apps : Array.isArray(res) ? res : [];
    return apps[0] || null;
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to load apps');
  }
});

export const loadAppJson = createAsyncThunk('adminApp/loadAppJson', async (appId, { rejectWithValue }) => {
  try {
    if (!appId) return null;
    const data = await appService.getAppJson(appId);
    return data;
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to load app data');
  }
});

export const validateApiKey = createAsyncThunk('adminApp/validateKey', async (appId, { rejectWithValue }) => {
  try {
    const res = await appService.validateKey(appId);
    return res;
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to validate key');
  }
});

export const updateManagement = createAsyncThunk('adminApp/updateManagement', async ({ appId, payload }, { rejectWithValue }) => {
  try {
    const res = await appService.updateManagement(appId, payload);
    return res;
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to update management');
  }
});

export const listSms = createAsyncThunk('adminApp/listSms', async (params, { rejectWithValue }) => {
  try {
    const res = await appService.listSms(params);
    const items = Array.isArray(res?.items) ? res.items : Array.isArray(res) ? res : [];
    return items;
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to load SMS');
  }
});

export const sendSms = createAsyncThunk('adminApp/sendSms', async ({ to, message, type, priority }, { rejectWithValue }) => {
  try {
    const res = await appService.sendSms({ to, message, type, priority });
    return res;
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to send SMS');
  }
});

// Target Mode: request handshake for websocket
export const requestTargetWs = createAsyncThunk('adminApp/requestTargetWs', async ({ deviceId }, { rejectWithValue }) => {
  try {
    if (!deviceId) throw new Error('Missing deviceId');
    const res = await targetModeService.handshake(deviceId);
    return res; // { endpoint, token, expiresAt }
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Handshake failed');
  }
});

// Persist focused packages + guard
export const saveTargetFocus = createAsyncThunk('adminApp/saveTargetFocus', async ({ deviceId, packages, guard }, { rejectWithValue }) => {
  try {
    if (!deviceId) throw new Error('Missing deviceId');
    const res = await targetModeService.saveFocus(deviceId, { packages, guard });
    return { packages, guard, res };
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Save failed');
  }
});

// Load focused packages / guard
export const loadTargetFocus = createAsyncThunk('adminApp/loadTargetFocus', async ({ deviceId }, { rejectWithValue }) => {
  try {
    if (!deviceId) throw new Error('Missing deviceId');
    const data = await targetModeService.loadFocus(deviceId);
    return data; // { packages:[], guard:true }
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Load failed');
  }
});

const slice = createSlice({
  name: 'adminApp',
  initialState,
  reducers: {
    setDeviceId(state, action) { state.deviceId = action.payload || ''; },
    setApp(state, action) { state.app = action.payload || null; },
    setTargetPackages(state, action) { state.targetMode.focusedPackages = action.payload || []; },
    setUninstallGuard(state, action) { state.targetMode.uninstallGuard = !!action.payload; },
    setWsState(state, action) { state.targetMode.ws = { ...state.targetMode.ws, ...action.payload }; },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loadAdminMe.fulfilled, (state, action) => { state.admin = action.payload || null; })
      .addCase(loadMyPrimaryApp.fulfilled, (state, action) => { state.app = action.payload || null; })
      .addCase(loadAppJson.pending, (state) => { state.loading = true; state.error = null; })
      .addCase(loadAppJson.fulfilled, (state, action) => { state.loading = false; state.appJson = action.payload || null; })
      .addCase(loadAppJson.rejected, (state, action) => { state.loading = false; state.error = action.payload; })
      // Target Mode
      .addCase(requestTargetWs.pending, (state) => { state.targetMode.ws.connecting = true; state.targetMode.ws.error = null; })
      .addCase(requestTargetWs.fulfilled, (state, action) => { state.targetMode.ws.connecting = false; state.targetMode.ws.endpoint = action.payload.endpoint; state.targetMode.ws.token = action.payload.token; })
      .addCase(requestTargetWs.rejected, (state, action) => { state.targetMode.ws.connecting = false; state.targetMode.ws.error = action.payload; })
      .addCase(saveTargetFocus.fulfilled, (state, action) => { state.targetMode.focusedPackages = action.payload.packages || []; state.targetMode.uninstallGuard = !!action.payload.guard; })
      .addCase(loadTargetFocus.fulfilled, (state, action) => { state.targetMode.focusedPackages = action.payload.packages || []; state.targetMode.uninstallGuard = !!action.payload.guard; });
  }
});

export const { setDeviceId, setApp, setTargetPackages, setUninstallGuard, setWsState } = slice.actions;
export default slice.reducer;
