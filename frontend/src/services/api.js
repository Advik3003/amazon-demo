/**
 * Axios API Instance
 * ===================
 * Centralized HTTP client with:
 * - Base URL from config
 * - Automatic JWT token injection
 * - Token refresh on 401
 * - Request/Response logging (dev only)
 */

import axios from "axios";
import appConfig from "../config/appConfig";
import { store } from "../store/store";
import { setToken, logout } from "../store/slices/authSlice";

const api = axios.create({
  baseURL: appConfig.API_BASE_URL,
  timeout: appConfig.API_TIMEOUT,
  headers: { "Content-Type": "application/json" },
});

// ==================== REQUEST INTERCEPTOR ====================
// Automatically adds JWT token to every request
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("accessToken");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ==================== RESPONSE INTERCEPTOR ====================
// Handles 401 (token expired) by refreshing the token
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) prom.reject(error);
    else prom.resolve(token);
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // If 401 and not already retrying
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // Queue this request until token is refreshed
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return api(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = localStorage.getItem("refreshToken");
      if (!refreshToken) {
        store.dispatch(logout());
        return Promise.reject(error);
      }

      try {
        const response = await axios.post(
          `${appConfig.API_BASE_URL}/api/v1/auth/refresh`,
          { refreshToken }
        );
        const { accessToken, refreshToken: newRefreshToken } = response.data.data;

        store.dispatch(setToken({ accessToken, refreshToken: newRefreshToken }));
        processQueue(null, accessToken);

        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        store.dispatch(logout());
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default api;
