import { createSlice, createAsyncThunk } from "@reduxjs/toolkit";
import orderService from "../../services/orderService";

export const fetchOrders = createAsyncThunk("orders/fetchAll", async () => {
  const response = await orderService.getOrders();
  return response.data;
});

export const placeOrder = createAsyncThunk("orders/place", async (orderData, { rejectWithValue }) => {
  try {
    const response = await orderService.createOrder(orderData);
    return response.data;
  } catch (error) {
    return rejectWithValue(error.response?.data?.message || "Order failed");
  }
});

const ordersSlice = createSlice({
  name: "orders",
  initialState: { items: [], loading: false, error: null },
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchOrders.pending, (state) => { state.loading = true; })
      .addCase(fetchOrders.fulfilled, (state, action) => {
        state.loading = false;
        state.items = action.payload?.data?.content || [];
      })
      .addCase(fetchOrders.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message;
      });
  },
});

export default ordersSlice.reducer;
