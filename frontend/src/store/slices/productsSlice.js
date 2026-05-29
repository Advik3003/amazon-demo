import { createSlice, createAsyncThunk } from "@reduxjs/toolkit";
import productService from "../../services/productService";

export const fetchProducts = createAsyncThunk("products/fetchAll", async (params) => {
  const response = await productService.getProducts(params);
  return response.data;
});

export const fetchFeatured = createAsyncThunk("products/fetchFeatured", async () => {
  const response = await productService.getFeatured();
  return response.data;
});

export const searchProducts = createAsyncThunk("products/search", async (query) => {
  const response = await productService.search(query);
  return response.data;
});

const productsSlice = createSlice({
  name: "products",
  initialState: {
    items: [],
    featured: [],
    searchResults: [],
    totalElements: 0,
    totalPages: 0,
    currentPage: 0,
    loading: false,
    error: null,
  },
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchProducts.pending, (state) => { state.loading = true; })
      .addCase(fetchProducts.fulfilled, (state, action) => {
        state.loading = false;
        if (action.payload?.data) {
          state.items = action.payload.data.content || action.payload.data || [];
          state.totalElements = action.payload.data.totalElements || 0;
          state.totalPages = action.payload.data.totalPages || 0;
        }
      })
      .addCase(fetchProducts.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message;
      })
      .addCase(fetchFeatured.fulfilled, (state, action) => {
        state.featured = action.payload?.data || [];
      })
      .addCase(searchProducts.fulfilled, (state, action) => {
        state.searchResults = action.payload?.data?.content || [];
      });
  },
});

export default productsSlice.reducer;
