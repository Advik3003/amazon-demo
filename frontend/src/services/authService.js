import api from "./api";

const authService = {
  login: (credentials) => api.post("/api/v1/auth/login", credentials),
  register: (data) => api.post("/api/v1/auth/register", data),
  logout: (refreshToken) => api.post("/api/v1/auth/logout", { refreshToken }),
  refreshToken: (refreshToken) => api.post("/api/v1/auth/refresh", { refreshToken }),
  validateToken: () => api.get("/api/v1/auth/validate"),
};

export default authService;
