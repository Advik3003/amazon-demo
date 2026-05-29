package com.amazondemo.android.model;

import java.util.List;

public class ProductResponse {
    private String id;
    private String name;
    private String description;
    private double price;
    private double originalPrice;
    private double discountPercentage;
    private String categoryName;
    private String brand;
    private List<String> imageUrls;
    private double averageRating;
    private int reviewCount;
    private String status;
    private boolean featured;
    private int stockQuantity;
    private boolean inStock;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public double getOriginalPrice() { return originalPrice; }
    public double getDiscountPercentage() { return discountPercentage; }
    public String getCategoryName() { return categoryName; }
    public String getBrand() { return brand; }
    public List<String> getImageUrls() { return imageUrls; }
    public double getAverageRating() { return averageRating; }
    public int getReviewCount() { return reviewCount; }
    public boolean isInStock() { return inStock; }
    public String getFirstImageUrl() {
        return (imageUrls != null && !imageUrls.isEmpty()) ? imageUrls.get(0) : null;
    }
}
