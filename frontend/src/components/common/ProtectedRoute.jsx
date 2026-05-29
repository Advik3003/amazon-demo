import { Navigate, Outlet } from "react-router-dom";
import { useSelector } from "react-redux";

/**
 * Protected Route - Requires authentication
 * If not authenticated, redirects to login page.
 * `returnUrl` state allows redirecting back after login.
 */
const ProtectedRoute = () => {
  const { isAuthenticated } = useSelector((state) => state.auth);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
};

export default ProtectedRoute;
