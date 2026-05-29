import { useState } from "react";
import {
  Box, Typography, Grid, Paper, Tab, Tabs, Table, TableBody,
  TableCell, TableHead, TableRow, Button, Chip, Alert,
} from "@mui/material";
import { useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";

const AdminPage = () => {
  const [tab, setTab] = useState(0);
  const { user } = useSelector((s) => s.auth);
  const navigate = useNavigate();

  if (!user?.roles?.includes("ROLE_ADMIN")) {
    return (
      <Box sx={{ p: 4 }}>
        <Alert severity="error">Access Denied: Admin role required</Alert>
        <Button variant="contained" sx={{ mt: 2 }} onClick={() => navigate("/")}>Go Home</Button>
      </Box>
    );
  }

  const stats = [
    { label: "Total Users", value: "1,247", color: "primary.main" },
    { label: "Total Orders", value: "3,891", color: "success.main" },
    { label: "Revenue", value: "$48,230", color: "warning.main" },
    { label: "Products", value: "426", color: "info.main" },
  ];

  const demoOrders = [
    { id: "ORD-001", user: "john@example.com", amount: "$149.97", status: "DELIVERED", date: "2024-01-15" },
    { id: "ORD-002", user: "jane@example.com", amount: "$79.99", status: "SHIPPED", date: "2024-01-28" },
    { id: "ORD-003", user: "bob@example.com", amount: "$39.98", status: "PENDING", date: "2024-02-01" },
  ];

  const statusColors = { PENDING: "warning", SHIPPED: "primary", DELIVERED: "success", CANCELLED: "error" };

  return (
    <Box sx={{ maxWidth: 1200, mx: "auto", px: 2, py: 3 }}>
      <Typography variant="h4" fontWeight={700} gutterBottom>Admin Dashboard</Typography>

      {/* Stats */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {stats.map((stat) => (
          <Grid item xs={6} md={3} key={stat.label}>
            <Paper elevation={2} sx={{ p: 2, textAlign: "center" }}>
              <Typography variant="h4" fontWeight={700} color={stat.color}>{stat.value}</Typography>
              <Typography variant="body2" color="text.secondary">{stat.label}</Typography>
            </Paper>
          </Grid>
        ))}
      </Grid>

      <Paper elevation={2}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ borderBottom: 1, borderColor: "divider" }}>
          <Tab label="Recent Orders" />
          <Tab label="Products" />
          <Tab label="Users" />
        </Tabs>

        <Box sx={{ p: 2 }}>
          {tab === 0 && (
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Order ID</TableCell>
                  <TableCell>Customer</TableCell>
                  <TableCell>Amount</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Date</TableCell>
                  <TableCell>Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {demoOrders.map((order) => (
                  <TableRow key={order.id}>
                    <TableCell>{order.id}</TableCell>
                    <TableCell>{order.user}</TableCell>
                    <TableCell>{order.amount}</TableCell>
                    <TableCell><Chip label={order.status} size="small" color={statusColors[order.status] || "default"} /></TableCell>
                    <TableCell>{order.date}</TableCell>
                    <TableCell><Button size="small">View</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          {tab === 1 && (
            <Box sx={{ p: 2, textAlign: "center" }}>
              <Button variant="contained" onClick={() => navigate("/products")}>Manage Products</Button>
            </Box>
          )}
          {tab === 2 && (
            <Typography color="text.secondary" sx={{ p: 2 }}>User management coming soon</Typography>
          )}
        </Box>
      </Paper>
    </Box>
  );
};

export default AdminPage;
