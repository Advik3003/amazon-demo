package com.amazondemo.order.dto.v2;

import com.amazondemo.order.dto.OrderResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Response V2
 * ==================
 * Enhanced order payload for API v2.
 *
 * V2 additions over V1:
 *  - apiVersion      : Explicit version tag
 *  - statusHistory   : Timeline of all status transitions
 *  - shippingAddress : Structured object instead of flat fields
 *  - timeline        : Human-readable event timeline
 *  - estimatedDelivery: Calculated delivery date
 *  - canCancel       : Business rule pre-computed for UI
 *  - canReturn       : Business rule pre-computed for UI
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderResponseV2 {

    // --- Core fields (same as V1) ---
    private String id;
    private String orderNumber;
    private String userId;
    private List<OrderItemDtoV2> items;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal shippingCost;
    private BigDecimal totalAmount;
    private String status;
    private String paymentStatus;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- V2 NEW FIELDS ---
    private String apiVersion = "v2";
    private ShippingAddressV2 shippingAddress;
    private OrderTimelineV2 timeline;
    private String estimatedDelivery;
    private boolean canCancel;
    private boolean canReturn;
    private SummaryV2 summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddressV2 {
        private String fullName;
        private String street;
        private String city;
        private String state;
        private String zipCode;
        private String country;

        public String toSingleLine() {
            return String.join(", ", fullName, street, city, state, zipCode, country);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderTimelineV2 {
        private LocalDateTime orderedAt;
        private LocalDateTime confirmedAt;
        private LocalDateTime processingAt;
        private LocalDateTime shippedAt;
        private LocalDateTime deliveredAt;
        private LocalDateTime cancelledAt;
        private String currentStage;
        private String nextStage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryV2 {
        private int totalItems;
        private int totalQuantity;
        private BigDecimal savings;
        private boolean freeShipping;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDtoV2 {
        private String id;
        private String productId;
        private String productName;
        private String productImageUrl;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private String productStatus;       // V2 NEW: whether product still available
    }

    /** Factory: convert V1 response to V2 enriched payload */
    public static OrderResponseV2 fromV1(OrderResponse v1) {
        List<OrderItemDtoV2> v2Items = null;
        if (v1.getItems() != null) {
            v2Items = v1.getItems().stream().map(item ->
                OrderItemDtoV2.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .productImageUrl(item.getProductImageUrl())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .totalPrice(item.getTotalPrice())
                    .productStatus("AVAILABLE")
                    .build()
            ).toList();
        }

        int totalQty = v2Items != null ? v2Items.stream().mapToInt(OrderItemDtoV2::getQuantity).sum() : 0;

        String status = v1.getStatus();
        boolean canCancel = "PENDING".equals(status) || "CONFIRMED".equals(status);
        boolean canReturn  = "DELIVERED".equals(status);

        String nextStage = switch (status != null ? status : "") {
            case "PENDING"    -> "CONFIRMED";
            case "CONFIRMED"  -> "PROCESSING";
            case "PROCESSING" -> "SHIPPED";
            case "SHIPPED"    -> "DELIVERED";
            default           -> null;
        };

        return OrderResponseV2.builder()
                .id(v1.getId())
                .orderNumber(v1.getOrderNumber())
                .userId(v1.getUserId())
                .items(v2Items)
                .subtotal(v1.getSubtotal())
                .taxAmount(v1.getTaxAmount())
                .shippingCost(v1.getShippingCost())
                .totalAmount(v1.getTotalAmount())
                .status(v1.getStatus())
                .paymentStatus(v1.getPaymentStatus())
                .notes(v1.getNotes())
                .createdAt(v1.getCreatedAt())
                .updatedAt(v1.getUpdatedAt())
                .apiVersion("v2")
                .shippingAddress(ShippingAddressV2.builder()
                        .fullName(v1.getShippingFullName())
                        .street(v1.getShippingStreet())
                        .city(v1.getShippingCity())
                        .state(v1.getShippingState())
                        .zipCode(v1.getShippingZipCode())
                        .country(v1.getShippingCountry())
                        .build())
                .timeline(OrderTimelineV2.builder()
                        .orderedAt(v1.getCreatedAt())
                        .currentStage(v1.getStatus())
                        .nextStage(nextStage)
                        .build())
                .estimatedDelivery(canCancel ? "3-5 business days" : "Delivered")
                .canCancel(canCancel)
                .canReturn(canReturn)
                .summary(SummaryV2.builder()
                        .totalItems(v2Items != null ? v2Items.size() : 0)
                        .totalQuantity(totalQty)
                        .freeShipping(v1.getShippingCost() != null &&
                                      v1.getShippingCost().compareTo(BigDecimal.ZERO) == 0)
                        .build())
                .build();
    }
}
