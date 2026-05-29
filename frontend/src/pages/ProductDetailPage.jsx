import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useDispatch } from "react-redux";
import {
  Box, Grid, Typography, Button, Chip, Rating, Divider,
  Paper, TextField, Breadcrumbs, Link, CircularProgress,
} from "@mui/material";
import { ShoppingCart, Favorite, Share } from "@mui/icons-material";
import { addToCart } from "../store/slices/cartSlice";
import productService from "../services/productService";
import appConfig from "../config/appConfig";
import toast from "react-hot-toast";

const ProductDetailPage = () => {
  const { id } = useParams();
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [quantity, setQuantity] = useState(1);
  const [selectedImage, setSelectedImage] = useState(0);

  useEffect(() => {
    const fetchProduct = async () => {
      try {
        const response = await productService.getProduct(id);
        setProduct(response.data?.data || response.data);
      } catch {
        setProduct(getDemoProduct(id));
      } finally {
        setLoading(false);
      }
    };
    fetchProduct();
  }, [id]);

  if (loading) return <Box sx={{ display: "flex", justifyContent: "center", p: 8 }}><CircularProgress /></Box>;

  if (!product) return <Typography sx={{ p: 4 }}>Product not found</Typography>;

  const handleAddToCart = () => {
    dispatch(addToCart({ id: product.id, name: product.name, price: product.price, imageUrl: product.imageUrls?.[0], quantity }));
    toast.success(`${product.name} added to cart!`);
  };

  const handleBuyNow = () => {
    dispatch(addToCart({ id: product.id, name: product.name, price: product.price, imageUrl: product.imageUrls?.[0], quantity }));
    navigate("/cart");
  };

  const images = product.imageUrls?.length > 0
    ? product.imageUrls
    : [`https://picsum.photos/seed/${product.id}/600/400`];

  return (
    <Box sx={{ maxWidth: 1200, mx: "auto", px: 2, py: 3 }}>
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link href="/" underline="hover">Home</Link>
        <Link href="/products" underline="hover">Products</Link>
        <Typography color="text.primary">{product.name}</Typography>
      </Breadcrumbs>

      <Grid container spacing={4}>
        {/* Images */}
        <Grid item xs={12} md={5}>
          <Box component="img"
            src={images[selectedImage]}
            alt={product.name}
            sx={{ width: "100%", borderRadius: 1, border: "1px solid", borderColor: "divider" }}
          />
          {images.length > 1 && (
            <Box sx={{ display: "flex", gap: 1, mt: 1, flexWrap: "wrap" }}>
              {images.map((img, i) => (
                <Box key={i} component="img" src={img} sx={{ width: 60, height: 60, cursor: "pointer", border: i === selectedImage ? "2px solid" : "1px solid", borderColor: i === selectedImage ? "primary.main" : "divider", borderRadius: 1 }}
                  onClick={() => setSelectedImage(i)} />
              ))}
            </Box>
          )}
        </Grid>

        {/* Details */}
        <Grid item xs={12} md={7}>
          <Typography variant="h5" fontWeight={700}>{product.name}</Typography>

          {product.brand && <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>Brand: <strong>{product.brand}</strong></Typography>}

          <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 2 }}>
            <Rating value={product.averageRating || 0} precision={0.5} readOnly size="small" />
            <Typography variant="body2" color="text.secondary">
              {product.averageRating?.toFixed(1) || "0"} ({product.reviewCount || 0} reviews)
            </Typography>
          </Box>

          <Divider sx={{ mb: 2 }} />

          <Box sx={{ display: "flex", alignItems: "baseline", gap: 2, mb: 1 }}>
            <Typography variant="h4" color="primary.main" fontWeight={700}>
              {appConfig.CURRENCY_SYMBOL}{product.price?.toFixed(2)}
            </Typography>
            {product.originalPrice > product.price && (
              <>
                <Typography variant="body1" sx={{ textDecoration: "line-through", color: "text.secondary" }}>
                  {appConfig.CURRENCY_SYMBOL}{product.originalPrice?.toFixed(2)}
                </Typography>
                <Chip label={`${Math.round(product.discountPercentage || 0)}% off`} color="error" size="small" />
              </>
            )}
          </Box>

          <Typography variant="body2" color={product.inStock !== false ? "success.main" : "error.main"} fontWeight={600} sx={{ mb: 2 }}>
            {product.inStock !== false ? "✓ In Stock" : "✗ Out of Stock"}
          </Typography>

          <Typography variant="body1" color="text.secondary" sx={{ mb: 3, lineHeight: 1.8 }}>
            {product.description || "No description available."}
          </Typography>

          <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 3 }}>
            <Typography variant="body2" fontWeight={600}>Quantity:</Typography>
            <TextField
              type="number"
              size="small"
              value={quantity}
              onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
              sx={{ width: 80 }}
              inputProps={{ min: 1, max: 99 }}
            />
          </Box>

          <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
            <Button variant="contained" size="large" startIcon={<ShoppingCart />}
              onClick={handleAddToCart} disabled={product.inStock === false}>
              Add to Cart
            </Button>
            <Button variant="outlined" size="large" onClick={handleBuyNow}
              disabled={product.inStock === false}>
              Buy Now
            </Button>
            <Button variant="outlined" size="large"><Favorite /></Button>
          </Box>
        </Grid>
      </Grid>
    </Box>
  );
};

const getDemoProduct = (id) => ({
  id,
  name: `Premium Product ${id}`,
  brand: "BrandName",
  price: 79.99,
  originalPrice: 99.99,
  discountPercentage: 20,
  averageRating: 4.5,
  reviewCount: 234,
  inStock: true,
  description: "This is an amazing product with great features. Perfect for everyday use. High quality materials ensure long lasting durability.",
  imageUrls: [],
});

export default ProductDetailPage;
