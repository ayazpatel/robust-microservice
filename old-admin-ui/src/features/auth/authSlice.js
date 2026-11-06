import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { login as loginApi, logout as logoutApi, getUserProfile } from '../../api/authService.js';
import { getStoredToken, storeToken, removeToken } from '../../utils/jwt.js';

const initialState = {
  token: getStoredToken() || null,
  user: null,
  loading: false,
  error: null,
};

export const login = createAsyncThunk('auth/login', async (credentials, { rejectWithValue }) => {
  try {
    const res = await loginApi(credentials);
    if (!res.success) return rejectWithValue(res.message || 'Login failed');
    return res.token;
  } catch (e) {
    return rejectWithValue(e?.message || 'Login failed');
  }
});

export const fetchProfile = createAsyncThunk('auth/fetchProfile', async (_, { rejectWithValue }) => {
  try {
    const res = await getUserProfile();
    if (!res.success) return rejectWithValue(res.message || 'Failed to load profile');
    return res.data;
  } catch (e) {
    return rejectWithValue(e?.message || 'Failed to load profile');
  }
});

export const logout = createAsyncThunk('auth/logout', async () => {
  await logoutApi();
  return true;
});

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(login.pending, (state) => {
        state.loading = true; state.error = null;
      })
      .addCase(login.fulfilled, (state, action) => {
        state.loading = false; state.token = action.payload; state.error = null;
        storeToken(action.payload);
      })
      .addCase(login.rejected, (state, action) => {
        state.loading = false; state.error = action.payload || 'Login failed';
      })
      .addCase(fetchProfile.pending, (state) => {
        state.loading = true; state.error = null;
      })
      .addCase(fetchProfile.fulfilled, (state, action) => {
        state.loading = false; state.user = action.payload;
      })
      .addCase(fetchProfile.rejected, (state, action) => {
        state.loading = false; state.error = action.payload || 'Failed to load profile';
      })
      .addCase(logout.fulfilled, (state) => {
        state.token = null; state.user = null; removeToken();
      });
  }
});

export default authSlice.reducer;
