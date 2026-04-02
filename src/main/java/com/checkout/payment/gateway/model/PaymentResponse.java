package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
  private UUID id;
  private PaymentStatus status;
  private String cardNumberLastFour;
  private Integer expiryMonth;
  private Integer expiryYear;
  private String currency;
  private Long amount;
  private String idempotencyKey;
  private Integer retryCount;
}
