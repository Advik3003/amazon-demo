import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate, Link } from "react-router-dom";
import { Box, Paper, Typography, TextField, Button, Alert, CircularProgress } from "@mui/material";
import { register, clearError } from "../store/slices/authSlice";
import toast from "react-hot-toast";
import appConfig from "../config/appConfig";

const RegisterPage = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { loading, error } = useSelector((s) => s.auth);
  const [formData, setFormData] = useState({ firstName: "", lastName: "", email: "", password: "" });
  const [passwordError, setPasswordError] = useState("");

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
    if (error) dispatch(clearError());
    if (e.target.name === "password") {
      setPasswordError(e.target.value.length < 8 ? "Password must be at least 8 characters" : "");
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (passwordError) return;
    const result = await dispatch(register(formData));
    if (register.fulfilled.match(result)) {
      toast.success("Account created successfully!");
      navigate("/");
    }
  };

  return (
    <Box sx={{ minHeight: "calc(100vh - 128px)", display: "flex", alignItems: "center", justifyContent: "center", bgcolor: "background.default", py: 4 }}>
      <Paper elevation={3} sx={{ p: 4, width: "100%", maxWidth: 450 }}>
        <Typography variant="h5" fontWeight={700} gutterBottom align="center">
          Create {appConfig.APP_NAME} Account
        </Typography>

        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        <Box component="form" onSubmit={handleSubmit} sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
          <Box sx={{ display: "flex", gap: 2 }}>
            <TextField label="First Name" name="firstName" value={formData.firstName} onChange={handleChange} required fullWidth />
            <TextField label="Last Name" name="lastName" value={formData.lastName} onChange={handleChange} required fullWidth />
          </Box>
          <TextField label="Email Address" name="email" type="email" value={formData.email} onChange={handleChange} required fullWidth />
          <TextField
            label="Password"
            name="password"
            type="password"
            value={formData.password}
            onChange={handleChange}
            required
            fullWidth
            error={!!passwordError}
            helperText={passwordError || "At least 8 characters"}
          />
          <Button type="submit" variant="contained" size="large" fullWidth disabled={loading || !!passwordError}>
            {loading ? <CircularProgress size={24} /> : "Create Account"}
          </Button>
        </Box>

        <Typography variant="body2" align="center" sx={{ mt: 2 }}>
          Already have an account?{" "}
          <Link to="/login" style={{ color: "inherit", fontWeight: 600 }}>Sign In</Link>
        </Typography>
      </Paper>
    </Box>
  );
};

export default RegisterPage;
