package com.amazondemo.android.model;

import java.util.List;

public class OrderResponse {
    private String id;
    private String orderNumber;
    private double totalAmount;
    private String status;
    private String paymentStatus;
    private String createdAt;
    private List<OrderItemDto> items;

    public String getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public double getTotalAmount() { return totalAmount; }
    public String getStatus() { return status; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getCreatedAt() { return createdAt; }
    public List<OrderItemDto> getItems() { return items; }

    public static class OrderItemDto {
        private String productId;
        private String productName;
        private int quantity;
        private double unitPrice;
        public String getProductName() { return productName; }
        public int getQuantity() { return quantity; }
        public double getUnitPrice() { return unitPrice; }
    }
}
