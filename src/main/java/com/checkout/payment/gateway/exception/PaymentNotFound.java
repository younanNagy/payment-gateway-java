package com.checkout.payment.gateway.exception;

import lombok.Getter;

@Getter
public class PaymentNotFound extends RuntimeException {

  private final String invalidId;

  public PaymentNotFound(String invalidId) {
    super("Payment not found: " + invalidId);
    this.invalidId = invalidId;
  }
}
