package com.amazondemo.payment.repository;

import com.amazondemo.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByOrderId(String orderId);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    List<Payment> findByUserId(String userId);
}
