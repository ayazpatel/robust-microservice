import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { adminService } from '../../api/adminService.js';

const initialState = {
  items: [],
  loading: false,
  error: null,
};

export const fetchAdmins = createAsyncThunk('admins/fetchAll', async (_, { rejectWithValue }) => {
  try {
    const data = await adminService.getAll();
    return Array.isArray(data?.admins) ? data.admins : [];
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to load admins');
  }
});

export const createAdmin = createAsyncThunk('admins/create', async (payload, { rejectWithValue }) => {
  try {
    // Payload must include: { username, password, expiryInDays, categoryId }
    // Response: { message, admin, app }
    const res = await adminService.create(payload);
    return res;
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to create admin');
  }
});

export const updateAdminPassword = createAsyncThunk('admins/updatePassword', async ({ id, newPassword }, { rejectWithValue }) => {
  try {
    await adminService.updatePassword(id, newPassword);
    return { id };
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to update password');
  }
});

export const updateAdminExpiry = createAsyncThunk('admins/updateExpiry', async ({ id, expiryInDays }, { rejectWithValue }) => {
  try {
    await adminService.updateExpiry(id, expiryInDays);
    return { id };
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to update expiry');
  }
});

export const deleteAdmin = createAsyncThunk('admins/delete', async (id, { rejectWithValue }) => {
  try {
    await adminService.remove(id);
    return id;
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to delete admin');
  }
});

export const assignAdminCategories = createAsyncThunk('admins/assignCategories', async ({ id, categoryIds, mode }, { rejectWithValue }) => {
  try {
    await adminService.assignCategories(id, categoryIds, mode);
    return { id };
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to update categories');
  }
});

// Fetch categories assigned to a specific admin (for details/assignment UIs)
export const getAdminAssignedCategories = createAsyncThunk(
  'admins/getAssignedCategories',
  async (id, { rejectWithValue }) => {
    try {
      const res = await adminService.getAssignedCategories(id);
      // expecting { categories: [...] }
      return res?.categories || [];
    } catch (e) {
      return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to load assigned categories');
    }
  }
);

const adminsSlice = createSlice({
  name: 'admins',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchAdmins.pending, (state) => { state.loading = true; state.error = null; })
      .addCase(fetchAdmins.fulfilled, (state, action) => { state.loading = false; state.items = action.payload; })
      .addCase(fetchAdmins.rejected, (state, action) => { state.loading = false; state.error = action.payload; })
  // createAdmin returns { message, admin, app }; list will be refreshed at the component level
  .addCase(createAdmin.fulfilled, () => {})
      .addCase(deleteAdmin.fulfilled, (state, action) => { state.items = state.items.filter(a => a.id !== action.payload); });
  }
});

export default adminsSlice.reducer;
