/**
 * Redux Store Configuration
 * ==========================
 * Centralized state management using Redux Toolkit.
 *
 * SLICES (state domains):
 * - auth: User authentication state (token, user info, login status)
 * - cart: Shopping cart items
 * - theme: UI theme (dark/light mode, color theme)
 * - products: Product listing and search state
 * - orders: Order history
 * - notifications: In-app notifications
 */

import { configureStore } from "@reduxjs/toolkit";
import authReducer from "./slices/authSlice";
import cartReducer from "./slices/cartSlice";
import themeReducer from "./slices/themeSlice";
import productsReducer from "./slices/productsSlice";
import ordersReducer from "./slices/ordersSlice";
import notificationsReducer from "./slices/notificationsSlice";

export const store = configureStore({
  reducer: {
    auth: authReducer,
    cart: cartReducer,
    theme: themeReducer,
    products: productsReducer,
    orders: ordersReducer,
    notifications: notificationsReducer,
  },
  // Redux DevTools - disable in production
  devTools: import.meta.env.DEV,
});

export default store;
