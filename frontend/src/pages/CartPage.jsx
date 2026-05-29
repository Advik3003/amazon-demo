import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import {
  Box, Typography, Grid, Paper, Button, IconButton,
  TextField, Divider, Alert,
} from "@mui/material";
import { Delete, Add, Remove, ShoppingBag } from "@mui/icons-material";
import { removeFromCart, updateQuantity, clearCart } from "../store/slices/cartSlice";
import appConfig from "../config/appConfig";

const CartPage = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { items, total } = useSelector((s) => s.cart);
  const { isAuthenticated } = useSelector((s) => s.auth);

  if (items.length === 0) {
    return (
      <Box sx={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", minHeight: "60vh", gap: 2 }}>
        <ShoppingBag sx={{ fontSize: 80, color: "text.secondary" }} />
        <Typography variant="h5" color="text.secondary">Your cart is empty</Typography>
        <Button variant="contained" onClick={() => navigate("/products")}>Shop Now</Button>
      </Box>
    );
  }

  const tax = total * 0.1;
  const shipping = total >= 50 ? 0 : 9.99;
  const orderTotal = total + tax + shipping;

  return (
    <Box sx={{ maxWidth: 1200, mx: "auto", px: 2, py: 3 }}>
      <Typography variant="h4" fontWeight={700} gutterBottom>Shopping Cart</Typography>

      <Grid container spacing={3}>
        {/* Cart Items */}
        <Grid item xs={12} md={8}>
          <Paper elevation={2} sx={{ p: 2 }}>
            {items.map((item, i) => (
              <Box key={item.id}>
                {i > 0 && <Divider sx={{ my: 2 }} />}
                <Box sx={{ display: "flex", gap: 2, alignItems: "flex-start" }}>
                  <Box
                    component="img"
                    src={item.imageUrl || `https://picsum.photos/seed/${item.id}/100/100`}
                    alt={item.name}
                    sx={{ width: 100, height: 100, objectFit: "cover", borderRadius: 1 }}
                  />
                  <Box sx={{ flexGrow: 1 }}>
                    <Typography variant="subtitle1" fontWeight={600}>{item.name}</Typography>
                    <Typography variant="h6" color="primary.main">
                      {appConfig.CURRENCY_SYMBOL}{item.price?.toFixed(2)}
                    </Typography>
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1, mt: 1 }}>
                      <IconButton size="small" onClick={() => {
                        if (item.quantity <= 1) dispatch(removeFromCart(item.id));
                        else dispatch(updateQuantity({ id: item.id, quantity: item.quantity - 1 }));
                      }}><Remove /></IconButton>
                      <Typography sx={{ minWidth: 30, textAlign: "center" }}>{item.quantity}</Typography>
                      <IconButton size="small" onClick={() => dispatch(updateQuantity({ id: item.id, quantity: item.quantity + 1 }))}><Add /></IconButton>
                      <IconButton size="small" color="error" onClick={() => dispatch(removeFromCart(item.id))} sx={{ ml: 2 }}><Delete /></IconButton>
                    </Box>
                  </Box>
                  <Typography variant="subtitle1" fontWeight={700}>
                    {appConfig.CURRENCY_SYMBOL}{(item.price * item.quantity).toFixed(2)}
                  </Typography>
                </Box>
              </Box>
            ))}
          </Paper>
        </Grid>

        {/* Order Summary */}
        <Grid item xs={12} md={4}>
          <Paper elevation={2} sx={{ p: 2 }}>
            <Typography variant="h6" fontWeight={700} gutterBottom>Order Summary</Typography>
            <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
              <Typography>Subtotal ({items.reduce((s, i) => s + i.quantity, 0)} items)</Typography>
              <Typography>{appConfig.CURRENCY_SYMBOL}{total.toFixed(2)}</Typography>
            </Box>
            <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
              <Typography>Shipping</Typography>
              <Typography color={shipping === 0 ? "success.main" : "text.primary"}>
                {shipping === 0 ? "FREE" : `${appConfig.CURRENCY_SYMBOL}${shipping.toFixed(2)}`}
              </Typography>
            </Box>
            <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
              <Typography>Tax (10%)</Typography>
              <Typography>{appConfig.CURRENCY_SYMBOL}{tax.toFixed(2)}</Typography>
            </Box>
            <Divider sx={{ my: 1 }} />
            <Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
              <Typography variant="h6" fontWeight={700}>Order Total</Typography>
              <Typography variant="h6" fontWeight={700} color="primary.main">
                {appConfig.CURRENCY_SYMBOL}{orderTotal.toFixed(2)}
              </Typography>
            </Box>
            {shipping === 0 && (
              <Alert severity="success" sx={{ mb: 2 }}>🎉 You qualify for FREE shipping!</Alert>
            )}
            <Button
              variant="contained"
              size="large"
              fullWidth
              onClick={() => isAuthenticated ? navigate("/checkout") : navigate("/login")}
            >
              {isAuthenticated ? "Proceed to Checkout" : "Sign In to Checkout"}
            </Button>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default CartPage;
