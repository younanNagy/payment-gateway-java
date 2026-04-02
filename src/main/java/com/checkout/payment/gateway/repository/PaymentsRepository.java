package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository {

  private final HashMap<UUID, PostPaymentResponse> payments = new HashMap<>();
  private final HashMap<String, PostPaymentResponse> paymentsByIdempotencyKey = new HashMap<>();

  public void add(PostPaymentResponse payment) {
    payments.put(payment.getId(), payment);
  }

  public void addWithIdempotencyKey(String idempotencyKey, PostPaymentResponse payment) {
    payments.put(payment.getId(), payment);
    paymentsByIdempotencyKey.put(idempotencyKey, payment);
  }

  public Optional<PostPaymentResponse> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

  public Optional<PostPaymentResponse> getByIdempotencyKey(String idempotencyKey) {
    return Optional.ofNullable(paymentsByIdempotencyKey.get(idempotencyKey));
  }
}
