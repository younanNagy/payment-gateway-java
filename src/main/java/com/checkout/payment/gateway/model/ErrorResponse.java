package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.List;
import lombok.Builder;

@Builder
public record ErrorResponse(PaymentStatus paymentStatus, String message, List<String> errors) {}
