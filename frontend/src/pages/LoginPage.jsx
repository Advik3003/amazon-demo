import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate, Link } from "react-router-dom";
import {
  Box, Paper, Typography, TextField, Button,
  Alert, CircularProgress, Divider,
} from "@mui/material";
import { login, clearError } from "../store/slices/authSlice";
import toast from "react-hot-toast";
import appConfig from "../config/appConfig";

const LoginPage = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { loading, error } = useSelector((s) => s.auth);

  const [formData, setFormData] = useState({ email: "", password: "" });

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
    if (error) dispatch(clearError());
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const result = await dispatch(login(formData));
    if (login.fulfilled.match(result)) {
      toast.success("Welcome back!");
      navigate("/");
    }
  };

  return (
    <Box
      sx={{
        minHeight: "calc(100vh - 128px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        bgcolor: "background.default",
        py: 4,
      }}
    >
      <Paper elevation={3} sx={{ p: 4, width: "100%", maxWidth: 400 }}>
        <Typography variant="h5" fontWeight={700} gutterBottom align="center">
          Sign In to {appConfig.APP_NAME}
        </Typography>

        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        <Box component="form" onSubmit={handleSubmit} sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
          <TextField
            label="Email Address"
            name="email"
            type="email"
            value={formData.email}
            onChange={handleChange}
            required
            fullWidth
            autoFocus
          />
          <TextField
            label="Password"
            name="password"
            type="password"
            value={formData.password}
            onChange={handleChange}
            required
            fullWidth
          />
          <Button
            type="submit"
            variant="contained"
            size="large"
            fullWidth
            disabled={loading}
          >
            {loading ? <CircularProgress size={24} /> : "Sign In"}
          </Button>
        </Box>

        <Divider sx={{ my: 2 }} />

        {/* Demo credentials */}
        <Box sx={{ bgcolor: "action.hover", p: 1.5, borderRadius: 1, mb: 2 }}>
          <Typography variant="caption" color="text.secondary">
            <strong>Demo credentials:</strong><br />
            Email: demo@amazondemo.com<br />
            Password: Demo@123
          </Typography>
        </Box>

        <Typography variant="body2" align="center">
          New customer?{" "}
          <Link to="/register" style={{ color: "inherit", fontWeight: 600 }}>
            Create your account
          </Link>
        </Typography>
      </Paper>
    </Box>
  );
};

export default LoginPage;
