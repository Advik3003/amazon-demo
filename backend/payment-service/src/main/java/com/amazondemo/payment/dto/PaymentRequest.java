package com.amazondemo.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    @NotBlank private String orderId;
    @NotBlank private String userId;
    @NotNull @DecimalMin("0.01") private BigDecimal amount;
    private String currency = "USD";
    private String paymentMethod = "CREDIT_CARD";
    // In real app: card number, CVV etc. - NEVER log these!
}
