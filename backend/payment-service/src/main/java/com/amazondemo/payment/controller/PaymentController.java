package com.amazondemo.payment.controller;

import com.amazondemo.common.response.ApiResponse;
import com.amazondemo.payment.dto.PaymentRequest;
import com.amazondemo.payment.dto.PaymentResponse;
import com.amazondemo.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/process")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.processPayment(request, idempotencyKey), "Payment processed"));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(
            @PathVariable String orderId) {
        PaymentResponse payment = paymentService.getPaymentByOrder(orderId);
        if (payment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(payment, "Payment fetched"));
    }
}
