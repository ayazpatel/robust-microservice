import { configureStore } from '@reduxjs/toolkit';
import authReducer from '../features/auth/authSlice.js';
import adminsReducer from '../features/admins/adminsSlice.js';
import categoriesReducer from '../features/categories/categoriesSlice.js';
import adminAppReducer from '../features/adminApp/adminAppSlice.js';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    admins: adminsReducer,
    categories: categoriesReducer,
    adminApp: adminAppReducer,
  },
  // Default middleware includes thunk and serializable checks
});

export default store;
