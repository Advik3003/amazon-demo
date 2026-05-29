import { useSelector } from "react-redux";
import { Box, Paper, Typography, Avatar, Grid, Divider } from "@mui/material";

const ProfilePage = () => {
  const { user } = useSelector((s) => s.auth);

  return (
    <Box sx={{ maxWidth: 800, mx: "auto", px: 2, py: 3 }}>
      <Typography variant="h4" fontWeight={700} gutterBottom>My Profile</Typography>
      <Paper elevation={2} sx={{ p: 3 }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 3, mb: 3 }}>
          <Avatar sx={{ width: 80, height: 80, bgcolor: "primary.main", fontSize: 32 }}>
            {user?.firstName?.[0] || "U"}
          </Avatar>
          <Box>
            <Typography variant="h5" fontWeight={700}>
              {user?.firstName} {user?.lastName}
            </Typography>
            <Typography color="text.secondary">{user?.email}</Typography>
            <Box sx={{ display: "flex", gap: 1, mt: 0.5, flexWrap: "wrap" }}>
              {user?.roles?.map((role) => (
                <Typography key={role} variant="caption" sx={{ bgcolor: "primary.main", color: "white", px: 1, borderRadius: 1 }}>
                  {role}
                </Typography>
              ))}
            </Box>
          </Box>
        </Box>
        <Divider sx={{ mb: 2 }} />
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6}><Typography color="text.secondary">User ID</Typography><Typography fontWeight={600}>{user?.userId || "N/A"}</Typography></Grid>
          <Grid item xs={12} sm={6}><Typography color="text.secondary">Email</Typography><Typography fontWeight={600}>{user?.email}</Typography></Grid>
          <Grid item xs={12} sm={6}><Typography color="text.secondary">Member Since</Typography><Typography fontWeight={600}>2024</Typography></Grid>
          <Grid item xs={12} sm={6}><Typography color="text.secondary">Total Orders</Typography><Typography fontWeight={600}>-</Typography></Grid>
        </Grid>
      </Paper>
    </Box>
  );
};

export default ProfilePage;
