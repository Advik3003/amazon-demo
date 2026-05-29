import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import {
  Box, Typography, Grid, Card, CardMedia, CardContent,
  CardActionArea, Button, Chip, Skeleton, Paper,
} from "@mui/material";
import { LocalShipping, Security, Support, Refresh } from "@mui/icons-material";
import { fetchFeatured } from "../store/slices/productsSlice";
import { addToCart } from "../store/slices/cartSlice";
import appConfig from "../config/appConfig";
import toast from "react-hot-toast";

// Hero Banner Component
const HeroBanner = () => {
  const navigate = useNavigate();
  return (
    <Box
      sx={{
        background: "linear-gradient(135deg, #131921 0%, #1a2e4a 100%)",
        color: "white",
        py: { xs: 6, md: 10 },
        px: 2,
        textAlign: "center",
      }}
    >
      <Typography variant="h3" fontWeight={700} gutterBottom>
        Welcome to {appConfig.APP_NAME}
      </Typography>
      <Typography variant="h6" sx={{ opacity: 0.8, mb: 4 }}>
        {appConfig.APP_TAGLINE}
      </Typography>
      <Box sx={{ display: "flex", gap: 2, justifyContent: "center", flexWrap: "wrap" }}>
        <Button
          variant="contained"
          size="large"
          color="primary"
          onClick={() => navigate("/products")}
        >
          Shop Now
        </Button>
        <Button
          variant="outlined"
          size="large"
          sx={{ color: "white", borderColor: "white" }}
          onClick={() => navigate("/register")}
        >
          Create Account
        </Button>
      </Box>
    </Box>
  );
};

// Feature highlight card
const FeatureCard = ({ icon, title, desc }) => (
  <Paper elevation={2} sx={{ p: 3, textAlign: "center", height: "100%" }}>
    <Box sx={{ color: "primary.main", mb: 1 }}>{icon}</Box>
    <Typography variant="subtitle1" fontWeight={700}>{title}</Typography>
    <Typography variant="body2" color="text.secondary">{desc}</Typography>
  </Paper>
);

// Product Card Component
const ProductCard = ({ product }) => {
  const dispatch = useDispatch();
  const navigate = useNavigate();

  const handleAddToCart = (e) => {
    e.stopPropagation();
    dispatch(addToCart({
      id: product.id,
      name: product.name,
      price: product.price,
      imageUrl: product.imageUrls?.[0],
    }));
    toast.success(`${product.name} added to cart!`);
  };

  return (
    <Card sx={{ height: "100%", display: "flex", flexDirection: "column" }}>
      <CardActionArea onClick={() => navigate(`/products/${product.id}`)}>
        <CardMedia
          component="img"
          height="200"
          image={product.imageUrls?.[0] || `https://picsum.photos/seed/${product.id}/300/200`}
          alt={product.name}
          sx={{ objectFit: "cover" }}
        />
        <CardContent sx={{ flexGrow: 1 }}>
          <Typography variant="subtitle2" noWrap title={product.name}>
            {product.name}
          </Typography>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1, mt: 0.5 }}>
            <Typography variant="h6" color="primary.main" fontWeight={700}>
              {appConfig.CURRENCY_SYMBOL}{product.price?.toFixed(2)}
            </Typography>
            {product.originalPrice && product.originalPrice > product.price && (
              <Typography variant="body2" sx={{ textDecoration: "line-through", color: "text.secondary" }}>
                {appConfig.CURRENCY_SYMBOL}{product.originalPrice.toFixed(2)}
              </Typography>
            )}
          </Box>
          {product.discountPercentage > 0 && (
            <Chip label={`${Math.round(product.discountPercentage)}% off`} color="error" size="small" />
          )}
          <Typography variant="caption" color="text.secondary" display="block">
            ⭐ {product.averageRating?.toFixed(1) || "New"} ({product.reviewCount || 0} reviews)
          </Typography>
        </CardContent>
      </CardActionArea>
      <Box sx={{ p: 1 }}>
        <Button fullWidth variant="contained" size="small" onClick={handleAddToCart}>
          Add to Cart
        </Button>
      </Box>
    </Card>
  );
};

const HomePage = () => {
  const dispatch = useDispatch();
  const { featured, loading } = useSelector((s) => s.products);

  useEffect(() => {
    dispatch(fetchFeatured());
  }, [dispatch]);

  const features = [
    { icon: <LocalShipping fontSize="large" />, title: "Free Delivery", desc: "Free shipping on orders over $50" },
    { icon: <Security fontSize="large" />, title: "Secure Payment", desc: "100% secure payment processing" },
    { icon: <Support fontSize="large" />, title: "24/7 Support", desc: "Round the clock customer support" },
    { icon: <Refresh fontSize="large" />, title: "Easy Returns", desc: "30-day return policy" },
  ];

  // Dummy categories for quick navigation
  const categories = [
    { name: "Electronics", emoji: "📱", id: "electronics" },
    { name: "Clothing", emoji: "👕", id: "clothing" },
    { name: "Books", emoji: "📚", id: "books" },
    { name: "Home", emoji: "🏠", id: "home" },
    { name: "Sports", emoji: "⚽", id: "sports" },
    { name: "Toys", emoji: "🧸", id: "toys" },
  ];

  return (
    <Box>
      <HeroBanner />

      {/* Features */}
      <Box sx={{ maxWidth: 1200, mx: "auto", px: 2, py: 4 }}>
        <Grid container spacing={2}>
          {features.map((f) => (
            <Grid item xs={12} sm={6} md={3} key={f.title}>
              <FeatureCard {...f} />
            </Grid>
          ))}
        </Grid>
      </Box>

      {/* Categories */}
      <Box sx={{ bgcolor: "background.paper", py: 4 }}>
        <Box sx={{ maxWidth: 1200, mx: "auto", px: 2 }}>
          <Typography variant="h5" fontWeight={700} gutterBottom>
            Shop by Category
          </Typography>
          <Grid container spacing={2}>
            {categories.map((cat) => (
              <Grid item xs={6} sm={4} md={2} key={cat.id}>
                <Card
                  sx={{ textAlign: "center", cursor: "pointer", p: 2 }}
                  onClick={() => {}}
                >
                  <Typography fontSize="2rem">{cat.emoji}</Typography>
                  <Typography variant="body2" fontWeight={600}>{cat.name}</Typography>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Box>
      </Box>

      {/* Featured Products */}
      <Box sx={{ maxWidth: 1200, mx: "auto", px: 2, py: 4 }}>
        <Typography variant="h5" fontWeight={700} gutterBottom>
          Featured Products
        </Typography>
        <Grid container spacing={2}>
          {loading
            ? Array.from({ length: 8 }).map((_, i) => (
                <Grid item xs={12} sm={6} md={3} key={i}>
                  <Skeleton variant="rectangular" height={350} />
                </Grid>
              ))
            : (featured.length > 0 ? featured : getDemoProducts()).map((product) => (
                <Grid item xs={12} sm={6} md={3} key={product.id}>
                  <ProductCard product={product} />
                </Grid>
              ))}
        </Grid>
      </Box>
    </Box>
  );
};

// Demo products when backend is not running
const getDemoProducts = () => [
  { id: "1", name: "Wireless Headphones Pro", price: 79.99, originalPrice: 99.99, averageRating: 4.5, reviewCount: 234, imageUrls: [] },
  { id: "2", name: "Smart Watch Series 5", price: 199.99, originalPrice: 249.99, averageRating: 4.7, reviewCount: 512, imageUrls: [] },
  { id: "3", name: "Laptop Stand Adjustable", price: 35.99, originalPrice: 45.99, averageRating: 4.3, reviewCount: 89, imageUrls: [] },
  { id: "4", name: "USB-C Hub 7-in-1", price: 29.99, originalPrice: null, averageRating: 4.4, reviewCount: 156, imageUrls: [] },
  { id: "5", name: "Mechanical Keyboard RGB", price: 89.99, originalPrice: 119.99, averageRating: 4.6, reviewCount: 341, imageUrls: [] },
  { id: "6", name: "Noise Canceling Earbuds", price: 59.99, originalPrice: 79.99, averageRating: 4.2, reviewCount: 128, imageUrls: [] },
  { id: "7", name: "Gaming Mouse Wireless", price: 49.99, originalPrice: 69.99, averageRating: 4.8, reviewCount: 567, imageUrls: [] },
  { id: "8", name: "Portable Charger 20000mAh", price: 39.99, originalPrice: null, averageRating: 4.5, reviewCount: 223, imageUrls: [] },
];

export default HomePage;
