import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import {
  Box, Paper, Typography, TextField, Button, Grid, Stepper,
  Step, StepLabel, CircularProgress, Alert, Divider,
} from "@mui/material";
import { placeOrder } from "../store/slices/ordersSlice";
import { clearCart } from "../store/slices/cartSlice";
import appConfig from "../config/appConfig";
import toast from "react-hot-toast";

const steps = ["Shipping Address", "Payment", "Review"];

const CheckoutPage = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { items, total } = useSelector((s) => s.cart);
  const { loading } = useSelector((s) => s.orders);
  const [activeStep, setActiveStep] = useState(0);
  const [error, setError] = useState("");

  const [address, setAddress] = useState({
    shippingFullName: "", shippingStreet: "", shippingCity: "",
    shippingState: "", shippingZipCode: "", shippingCountry: "US",
  });

  const [payment, setPayment] = useState({ paymentMethod: "CREDIT_CARD" });

  const tax = total * 0.1;
  const shipping = total >= 50 ? 0 : 9.99;
  const orderTotal = total + tax + shipping;

  const handlePlaceOrder = async () => {
    const orderData = {
      items: items.map(item => ({
        productId: item.id,
        productName: item.name,
        quantity: item.quantity,
        productImageUrl: item.imageUrl,
      })),
      ...address,
    };

    const result = await dispatch(placeOrder(orderData));
    if (placeOrder.fulfilled.match(result)) {
      dispatch(clearCart());
      toast.success("Order placed successfully! 🎉");
      navigate("/orders");
    } else {
      setError(result.payload || "Failed to place order. Please try again.");
    }
  };

  if (items.length === 0) {
    navigate("/cart");
    return null;
  }

  return (
    <Box sx={{ maxWidth: 900, mx: "auto", px: 2, py: 3 }}>
      <Typography variant="h4" fontWeight={700} gutterBottom>Checkout</Typography>

      <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
        {steps.map((label) => <Step key={label}><StepLabel>{label}</StepLabel></Step>)}
      </Stepper>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError("")}>{error}</Alert>}

      <Grid container spacing={3}>
        <Grid item xs={12} md={7}>
          {activeStep === 0 && (
            <Paper elevation={2} sx={{ p: 3 }}>
              <Typography variant="h6" fontWeight={700} gutterBottom>Shipping Address</Typography>
              <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                <TextField label="Full Name" value={address.shippingFullName}
                  onChange={(e) => setAddress({ ...address, shippingFullName: e.target.value })} fullWidth required />
                <TextField label="Street Address" value={address.shippingStreet}
                  onChange={(e) => setAddress({ ...address, shippingStreet: e.target.value })} fullWidth required />
                <Box sx={{ display: "flex", gap: 2 }}>
                  <TextField label="City" value={address.shippingCity}
                    onChange={(e) => setAddress({ ...address, shippingCity: e.target.value })} fullWidth required />
                  <TextField label="State" value={address.shippingState}
                    onChange={(e) => setAddress({ ...address, shippingState: e.target.value })} fullWidth required />
                </Box>
                <Box sx={{ display: "flex", gap: 2 }}>
                  <TextField label="ZIP Code" value={address.shippingZipCode}
                    onChange={(e) => setAddress({ ...address, shippingZipCode: e.target.value })} fullWidth required />
                  <TextField label="Country" value={address.shippingCountry}
                    onChange={(e) => setAddress({ ...address, shippingCountry: e.target.value })} fullWidth required />
                </Box>
                <Button variant="contained" onClick={() => setActiveStep(1)}
                  disabled={!address.shippingFullName || !address.shippingStreet}>
                  Continue to Payment
                </Button>
              </Box>
            </Paper>
          )}

          {activeStep === 1 && (
            <Paper elevation={2} sx={{ p: 3 }}>
              <Typography variant="h6" fontWeight={700} gutterBottom>Payment Method</Typography>
              <Alert severity="info" sx={{ mb: 2 }}>This is a DEMO payment. No real payment is processed.</Alert>
              <TextField label="Card Number" fullWidth defaultValue="4111 1111 1111 1111" disabled sx={{ mb: 2 }} />
              <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
                <TextField label="Expiry" fullWidth defaultValue="12/26" disabled />
                <TextField label="CVV" fullWidth defaultValue="***" disabled />
              </Box>
              <Box sx={{ display: "flex", gap: 2 }}>
                <Button variant="outlined" onClick={() => setActiveStep(0)}>Back</Button>
                <Button variant="contained" onClick={() => setActiveStep(2)}>Review Order</Button>
              </Box>
            </Paper>
          )}

          {activeStep === 2 && (
            <Paper elevation={2} sx={{ p: 3 }}>
              <Typography variant="h6" fontWeight={700} gutterBottom>Review Your Order</Typography>
              {items.map((item) => (
                <Box key={item.id} sx={{ display: "flex", justifyContent: "space-between", py: 1 }}>
                  <Typography>{item.name} × {item.quantity}</Typography>
                  <Typography fontWeight={600}>{appConfig.CURRENCY_SYMBOL}{(item.price * item.quantity).toFixed(2)}</Typography>
                </Box>
              ))}
              <Divider sx={{ my: 2 }} />
              <Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
                <Typography variant="h6" fontWeight={700}>Total</Typography>
                <Typography variant="h6" fontWeight={700} color="primary.main">
                  {appConfig.CURRENCY_SYMBOL}{orderTotal.toFixed(2)}
                </Typography>
              </Box>
              <Box sx={{ display: "flex", gap: 2 }}>
                <Button variant="outlined" onClick={() => setActiveStep(1)}>Back</Button>
                <Button variant="contained" size="large" onClick={handlePlaceOrder} disabled={loading}>
                  {loading ? <CircularProgress size={24} /> : "Place Order"}
                </Button>
              </Box>
            </Paper>
          )}
        </Grid>

        <Grid item xs={12} md={5}>
          <Paper elevation={2} sx={{ p: 2 }}>
            <Typography variant="h6" fontWeight={700} gutterBottom>Order Summary</Typography>
            <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
              <Typography>Subtotal</Typography>
              <Typography>{appConfig.CURRENCY_SYMBOL}{total.toFixed(2)}</Typography>
            </Box>
            <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
              <Typography>Shipping</Typography>
              <Typography color={shipping === 0 ? "success.main" : "text.primary"}>
                {shipping === 0 ? "FREE" : `${appConfig.CURRENCY_SYMBOL}${shipping.toFixed(2)}`}
              </Typography>
            </Box>
            <Box sx={{ display: "flex", justifyContent: "space-between" }}>
              <Typography>Tax</Typography>
              <Typography>{appConfig.CURRENCY_SYMBOL}{tax.toFixed(2)}</Typography>
            </Box>
            <Divider sx={{ my: 1 }} />
            <Box sx={{ display: "flex", justifyContent: "space-between" }}>
              <Typography fontWeight={700}>Order Total</Typography>
              <Typography fontWeight={700} color="primary.main">
                {appConfig.CURRENCY_SYMBOL}{orderTotal.toFixed(2)}
              </Typography>
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default CheckoutPage;
