import { useEffect, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Box, Grid, Typography, Card, CardMedia, CardContent, CardActionArea,
  Button, Chip, Skeleton, Pagination, TextField, Select, MenuItem,
  FormControl, InputLabel, Paper,
} from "@mui/material";
import { addToCart } from "../store/slices/cartSlice";
import { fetchProducts, searchProducts } from "../store/slices/productsSlice";
import appConfig from "../config/appConfig";
import toast from "react-hot-toast";

const ProductsPage = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { items, loading, totalPages } = useSelector((s) => s.products);

  const [page, setPage] = useState(0);
  const [sortBy, setSortBy] = useState("createdAt");
  const [sortDir, setSortDir] = useState("desc");
  const query = searchParams.get("q") || "";

  useEffect(() => {
    if (query) {
      dispatch(searchProducts(query));
    } else {
      dispatch(fetchProducts({ page, size: 20, sortBy, sortDir }));
    }
  }, [page, sortBy, sortDir, query, dispatch]);

  const handleAddToCart = (e, product) => {
    e.stopPropagation();
    dispatch(addToCart({ id: product.id, name: product.name, price: product.price, imageUrl: product.imageUrls?.[0] }));
    toast.success(`${product.name} added to cart!`);
  };

  const displayItems = items.length > 0 ? items : getDemoItems();

  return (
    <Box sx={{ maxWidth: 1200, mx: "auto", px: 2, py: 3 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3, flexWrap: "wrap", gap: 2 }}>
        <Typography variant="h5" fontWeight={700}>
          {query ? `Search results for "${query}"` : "All Products"}
        </Typography>
        <Box sx={{ display: "flex", gap: 2 }}>
          <FormControl size="small" sx={{ minWidth: 130 }}>
            <InputLabel>Sort by</InputLabel>
            <Select value={sortBy} label="Sort by" onChange={(e) => setSortBy(e.target.value)}>
              <MenuItem value="createdAt">Newest</MenuItem>
              <MenuItem value="price">Price</MenuItem>
              <MenuItem value="averageRating">Rating</MenuItem>
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 100 }}>
            <InputLabel>Order</InputLabel>
            <Select value={sortDir} label="Order" onChange={(e) => setSortDir(e.target.value)}>
              <MenuItem value="asc">Ascending</MenuItem>
              <MenuItem value="desc">Descending</MenuItem>
            </Select>
          </FormControl>
        </Box>
      </Box>

      <Grid container spacing={2}>
        {loading
          ? Array.from({ length: 8 }).map((_, i) => (
              <Grid item xs={12} sm={6} md={3} key={i}>
                <Skeleton variant="rectangular" height={350} />
              </Grid>
            ))
          : displayItems.map((product) => (
              <Grid item xs={12} sm={6} md={3} key={product.id}>
                <Card sx={{ height: "100%", display: "flex", flexDirection: "column" }}>
                  <CardActionArea onClick={() => navigate(`/products/${product.id}`)}>
                    <CardMedia
                      component="img"
                      height="200"
                      image={product.imageUrls?.[0] || `https://picsum.photos/seed/${product.id}/300/200`}
                      alt={product.name}
                    />
                    <CardContent>
                      <Typography variant="subtitle2" noWrap>{product.name}</Typography>
                      <Box sx={{ display: "flex", alignItems: "center", gap: 1, mt: 0.5 }}>
                        <Typography variant="h6" color="primary.main" fontWeight={700}>
                          {appConfig.CURRENCY_SYMBOL}{product.price?.toFixed(2)}
                        </Typography>
                        {product.originalPrice > product.price && (
                          <Typography variant="body2" sx={{ textDecoration: "line-through", color: "text.secondary" }}>
                            {appConfig.CURRENCY_SYMBOL}{product.originalPrice?.toFixed(2)}
                          </Typography>
                        )}
                      </Box>
                      <Typography variant="caption" color="text.secondary">
                        ⭐ {product.averageRating?.toFixed(1) || "New"} ({product.reviewCount || 0})
                      </Typography>
                      {!product.inStock && <Chip label="Out of Stock" size="small" color="error" />}
                    </CardContent>
                  </CardActionArea>
                  <Box sx={{ p: 1, mt: "auto" }}>
                    <Button fullWidth variant="contained" size="small"
                      disabled={product.inStock === false}
                      onClick={(e) => handleAddToCart(e, product)}>
                      {product.inStock === false ? "Out of Stock" : "Add to Cart"}
                    </Button>
                  </Box>
                </Card>
              </Grid>
            ))}
      </Grid>

      {totalPages > 1 && (
        <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
          <Pagination count={totalPages} page={page + 1} onChange={(_, p) => setPage(p - 1)} color="primary" />
        </Box>
      )}
    </Box>
  );
};

const getDemoItems = () =>
  Array.from({ length: 12 }, (_, i) => ({
    id: String(i + 1),
    name: `Product ${i + 1}`,
    price: +(19.99 + i * 10).toFixed(2),
    originalPrice: +(29.99 + i * 10).toFixed(2),
    averageRating: 3.5 + Math.random(),
    reviewCount: Math.floor(Math.random() * 500),
    inStock: true,
    imageUrls: [],
  }));

export default ProductsPage;
