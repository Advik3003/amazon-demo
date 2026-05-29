import api from "./api";

const productService = {
  getProducts: (params = {}) => api.get("/api/v1/products", { params }),
  getProduct: (id) => api.get(`/api/v1/products/${id}`),
  getFeatured: () => api.get("/api/v1/products/featured"),
  search: (q, params = {}) => api.get("/api/v1/products/search", { params: { q, ...params } }),
  getByCategory: (categoryId, params = {}) =>
    api.get(`/api/v1/products/category/${categoryId}`, { params }),
  getCategories: () => api.get("/api/v1/categories"),
  createProduct: (data) => api.post("/api/v1/products", data),
  updateProduct: (id, data) => api.put(`/api/v1/products/${id}`, data),
  deleteProduct: (id) => api.delete(`/api/v1/products/${id}`),
};

export default productService;
