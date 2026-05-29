import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import {
  AppBar, Toolbar, Typography, InputBase, Badge, IconButton,
  Box, Menu, MenuItem, Avatar, Tooltip, Select, FormControl,
} from "@mui/material";
import {
  Search, ShoppingCart, AccountCircle, Brightness4, Brightness7,
  Notifications, Menu as MenuIcon,
} from "@mui/icons-material";
import { styled, alpha } from "@mui/material/styles";
import { toggleDarkMode, setColorTheme } from "../../store/slices/themeSlice";
import { logout } from "../../store/slices/authSlice";
import { THEME_CONFIGS } from "../../config/theme";
import appConfig from "../../config/appConfig";
import toast from "react-hot-toast";

const SearchBar = styled("div")(({ theme }) => ({
  position: "relative",
  borderRadius: theme.shape.borderRadius,
  backgroundColor: alpha(theme.palette.common.white, 0.15),
  "&:hover": { backgroundColor: alpha(theme.palette.common.white, 0.25) },
  marginLeft: theme.spacing(2),
  width: "auto",
  flexGrow: 1,
  maxWidth: 600,
  border: `1px solid ${theme.palette.primary.main}`,
}));

const SearchIconWrapper = styled("div")(({ theme }) => ({
  padding: theme.spacing(0, 2),
  height: "100%",
  position: "absolute",
  right: 0,
  top: 0,
  display: "flex",
  alignItems: "center",
  cursor: "pointer",
  backgroundColor: theme.palette.primary.main,
  borderRadius: "0 4px 4px 0",
  color: theme.palette.primary.contrastText,
}));

const StyledInputBase = styled(InputBase)(({ theme }) => ({
  color: "inherit",
  width: "100%",
  "& .MuiInputBase-input": {
    padding: theme.spacing(1, 1, 1, 2),
    width: "100%",
  },
}));

const Navbar = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const { isAuthenticated, user } = useSelector((s) => s.auth);
  const { mode, colorTheme } = useSelector((s) => s.theme);
  const cartCount = useSelector((s) => s.cart.items.reduce((sum, i) => sum + i.quantity, 0));
  const unreadCount = useSelector((s) => s.notifications.unreadCount);

  const [searchQuery, setSearchQuery] = useState("");
  const [anchorEl, setAnchorEl] = useState(null);

  const handleSearch = (e) => {
    if (e.key === "Enter" && searchQuery.trim()) {
      navigate(`/products?q=${encodeURIComponent(searchQuery)}`);
    }
  };

  const handleLogout = async () => {
    await dispatch(logout());
    toast.success("Logged out successfully");
    navigate("/");
    setAnchorEl(null);
  };

  return (
    <AppBar position="sticky" sx={{ bgcolor: "background.paper", color: "text.primary" }}>
      <Toolbar sx={{ gap: 1 }}>
        {/* Logo / Brand Name */}
        <Typography
          variant="h6"
          component={Link}
          to="/"
          sx={{ textDecoration: "none", color: "primary.main", fontWeight: 700, whiteSpace: "nowrap" }}
        >
          {appConfig.APP_NAME}
        </Typography>

        {/* Search Bar */}
        <SearchBar>
          <StyledInputBase
            placeholder="Search products..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={handleSearch}
          />
          <SearchIconWrapper onClick={() => navigate(`/products?q=${encodeURIComponent(searchQuery)}`)}>
            <Search fontSize="small" />
          </SearchIconWrapper>
        </SearchBar>

        <Box sx={{ flexGrow: 1 }} />

        {/* Theme Color Selector */}
        <FormControl size="small" sx={{ minWidth: 100, display: { xs: "none", md: "flex" } }}>
          <Select
            value={colorTheme}
            onChange={(e) => dispatch(setColorTheme(e.target.value))}
            sx={{ fontSize: "0.75rem" }}
          >
            {Object.values(THEME_CONFIGS).map((t) => (
              <MenuItem key={t.id} value={t.id}>{t.name}</MenuItem>
            ))}
          </Select>
        </FormControl>

        {/* Dark Mode Toggle */}
        <Tooltip title={mode === "dark" ? "Light mode" : "Dark mode"}>
          <IconButton onClick={() => dispatch(toggleDarkMode())} color="inherit">
            {mode === "dark" ? <Brightness7 /> : <Brightness4 />}
          </IconButton>
        </Tooltip>

        {/* Notifications */}
        {isAuthenticated && (
          <Tooltip title="Notifications">
            <IconButton color="inherit" onClick={() => navigate("/profile")}>
              <Badge badgeContent={unreadCount} color="error">
                <Notifications />
              </Badge>
            </IconButton>
          </Tooltip>
        )}

        {/* Cart */}
        <Tooltip title="Cart">
          <IconButton color="inherit" onClick={() => navigate("/cart")}>
            <Badge badgeContent={cartCount} color="primary">
              <ShoppingCart />
            </Badge>
          </IconButton>
        </Tooltip>

        {/* User Menu */}
        {isAuthenticated ? (
          <>
            <Tooltip title="Account">
              <IconButton onClick={(e) => setAnchorEl(e.currentTarget)}>
                <Avatar sx={{ width: 32, height: 32, bgcolor: "primary.main" }}>
                  {user?.firstName?.[0] || "U"}
                </Avatar>
              </IconButton>
            </Tooltip>
            <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}>
              <MenuItem component={Link} to="/profile" onClick={() => setAnchorEl(null)}>My Profile</MenuItem>
              <MenuItem component={Link} to="/orders" onClick={() => setAnchorEl(null)}>My Orders</MenuItem>
              {user?.roles?.includes("ROLE_ADMIN") && (
                <MenuItem component={Link} to="/admin" onClick={() => setAnchorEl(null)}>Admin Panel</MenuItem>
              )}
              <MenuItem onClick={handleLogout}>Logout</MenuItem>
            </Menu>
          </>
        ) : (
          <Box sx={{ display: "flex", gap: 1 }}>
            <Typography
              component={Link}
              to="/login"
              sx={{ textDecoration: "none", color: "inherit", fontWeight: 600, cursor: "pointer" }}
            >
              Sign In
            </Typography>
          </Box>
        )}
      </Toolbar>
    </AppBar>
  );
};

export default Navbar;
