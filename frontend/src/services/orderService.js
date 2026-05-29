import api from "./api";

const orderService = {
  getOrders: (params = {}) => api.get("/api/v1/orders", { params }),
  getOrder: (id) => api.get(`/api/v1/orders/${id}`),
  createOrder: (data) => api.post("/api/v1/orders", data),
  cancelOrder: (id, reason) => api.patch(`/api/v1/orders/${id}/cancel`, { reason }),
};

export default orderService;
