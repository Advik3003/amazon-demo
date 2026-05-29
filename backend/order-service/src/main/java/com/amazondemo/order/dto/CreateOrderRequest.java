package com.amazondemo.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @NotBlank(message = "Shipping name is required")
    private String shippingFullName;

    @NotBlank(message = "Shipping street is required")
    private String shippingStreet;

    @NotBlank(message = "Shipping city is required")
    private String shippingCity;

    @NotBlank(message = "Shipping state is required")
    private String shippingState;

    @NotBlank(message = "Shipping zip is required")
    private String shippingZipCode;

    @NotBlank(message = "Shipping country is required")
    private String shippingCountry;

    private String notes;

    @Data
    public static class OrderItemRequest {
        @NotBlank(message = "Product ID is required")
        private String productId;

        @NotNull(message = "Quantity is required")
        private Integer quantity;

        private String productName;
        private String productImageUrl;
    }
}
