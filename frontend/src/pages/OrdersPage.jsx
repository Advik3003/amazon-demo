import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import {
  Box, Typography, Paper, Chip, Button, Grid, CircularProgress,
} from "@mui/material";
import { fetchOrders } from "../store/slices/ordersSlice";
import appConfig from "../config/appConfig";

const statusColors = {
  PENDING: "warning",
  CONFIRMED: "info",
  PROCESSING: "info",
  SHIPPED: "primary",
  DELIVERED: "success",
  CANCELLED: "error",
};

const OrdersPage = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { items: orders, loading } = useSelector((s) => s.orders);

  useEffect(() => {
    dispatch(fetchOrders());
  }, [dispatch]);

  const displayOrders = orders.length > 0 ? orders : getDemoOrders();

  if (loading) return <Box sx={{ display: "flex", justifyContent: "center", p: 8 }}><CircularProgress /></Box>;

  return (
    <Box sx={{ maxWidth: 1000, mx: "auto", px: 2, py: 3 }}>
      <Typography variant="h4" fontWeight={700} gutterBottom>My Orders</Typography>

      {displayOrders.length === 0 ? (
        <Box sx={{ textAlign: "center", py: 8 }}>
          <Typography variant="h6" color="text.secondary" gutterBottom>No orders yet</Typography>
          <Button variant="contained" onClick={() => navigate("/products")}>Start Shopping</Button>
        </Box>
      ) : (
        displayOrders.map((order) => (
          <Paper key={order.id} elevation={2} sx={{ p: 3, mb: 2 }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: 2 }}>
              <Box>
                <Typography variant="subtitle1" fontWeight={700}>
                  Order #{order.orderNumber || order.id}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Placed: {order.createdAt ? new Date(order.createdAt).toLocaleDateString() : "Recently"}
                </Typography>
              </Box>
              <Box sx={{ textAlign: "right" }}>
                <Chip label={order.status || "PENDING"} color={statusColors[order.status] || "default"} size="small" />
                <Typography variant="h6" fontWeight={700} color="primary.main" sx={{ mt: 0.5 }}>
                  {appConfig.CURRENCY_SYMBOL}{order.totalAmount?.toFixed(2) || "0.00"}
                </Typography>
              </Box>
            </Box>

            {order.items && (
              <Box sx={{ mt: 2 }}>
                {order.items.map((item, i) => (
                  <Typography key={i} variant="body2" color="text.secondary">
                    • {item.productName || item.name} × {item.quantity}
                  </Typography>
                ))}
              </Box>
            )}
          </Paper>
        ))
      )}
    </Box>
  );
};

const getDemoOrders = () => [
  { id: "ord-1", orderNumber: "ORD-2024-001234", status: "DELIVERED", totalAmount: 149.97, createdAt: "2024-01-15", items: [{ productName: "Wireless Headphones", quantity: 1 }, { productName: "USB-C Hub", quantity: 2 }] },
  { id: "ord-2", orderNumber: "ORD-2024-001589", status: "SHIPPED", totalAmount: 79.99, createdAt: "2024-01-28", items: [{ productName: "Smart Watch", quantity: 1 }] },
  { id: "ord-3", orderNumber: "ORD-2024-002047", status: "PROCESSING", totalAmount: 39.98, createdAt: "2024-02-01", items: [{ productName: "Laptop Stand", quantity: 2 }] },
];

export default OrdersPage;
