import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { categoryService } from '../../api/categoryService.js';

const initialState = {
  items: [],
  stats: null,
  loading: false,
  error: null,
};

export const fetchCategories = createAsyncThunk('categories/fetch', async ({ includeInactive }, { rejectWithValue }) => {
  try {
    const { categories, stats } = await categoryService.list({ includeInactive });
    return { categories: categories || [], stats: stats || null };
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to load categories');
  }
});

export const createCategory = createAsyncThunk('categories/create', async (payload, { rejectWithValue }) => {
  try {
    const { data } = await categoryService.create(payload);
    return data?.category || null;
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to create category');
  }
});

export const updateCategory = createAsyncThunk('categories/update', async ({ id, patch }, { rejectWithValue }) => {
  try {
    const res = await categoryService.update(id, patch);
    return res?.category || null;
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to update category');
  }
});

export const toggleCategoryStatus = createAsyncThunk('categories/toggleStatus', async ({ id, active }, { rejectWithValue }) => {
  try {
    const res = await categoryService.toggleStatus(id, active);
    return res?.category || null;
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to update status');
  }
});

export const deleteCategory = createAsyncThunk('categories/delete', async (id, { rejectWithValue }) => {
  try {
    await categoryService.remove(id);
    return id;
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to delete category');
  }
});

export const assignCategoryAdmins = createAsyncThunk('categories/assignAdmins', async ({ id, adminIds, mode }, { rejectWithValue }) => {
  try {
    await categoryService.assignToAdmins(id, adminIds, mode);
    return { id };
  } catch (e) {
    return rejectWithValue(e?.response?.data?.message || e?.message || 'Failed to assign category');
  }
});

const categoriesSlice = createSlice({
  name: 'categories',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchCategories.pending, (state) => { state.loading = true; state.error = null; })
      .addCase(fetchCategories.fulfilled, (state, action) => {
        state.loading = false; state.items = action.payload.categories; state.stats = action.payload.stats;
      })
      .addCase(fetchCategories.rejected, (state, action) => { state.loading = false; state.error = action.payload; })
      .addCase(createCategory.fulfilled, (state, action) => {
        if (action.payload) state.items.unshift(action.payload);
      })
      .addCase(updateCategory.fulfilled, (state, action) => {
        const updated = action.payload;
        if (!updated) return;
        const idx = state.items.findIndex(c => c.id === updated.id);
        if (idx !== -1) state.items[idx] = { ...state.items[idx], ...updated };
      })
      .addCase(toggleCategoryStatus.fulfilled, (state, action) => {
        const updated = action.payload;
        if (!updated) return;
        const idx = state.items.findIndex(c => c.id === updated.id);
        if (idx !== -1) state.items[idx] = { ...state.items[idx], ...updated };
      })
      .addCase(deleteCategory.fulfilled, (state, action) => { state.items = state.items.filter(c => c.id !== action.payload); });
  }
});

export default categoriesSlice.reducer;
