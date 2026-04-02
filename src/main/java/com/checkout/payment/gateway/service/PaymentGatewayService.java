package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankSimulatorClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.PaymentNotFound;
import com.checkout.payment.gateway.model.BankSimulatorRequest;
import com.checkout.payment.gateway.model.BankSimulatorResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final BankSimulatorClient bankSimulatorClient;

  public PaymentGatewayService(
      PaymentsRepository paymentsRepository, BankSimulatorClient bankSimulatorClient) {
    this.paymentsRepository = paymentsRepository;
    this.bankSimulatorClient = bankSimulatorClient;
  }

  public PaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new PaymentNotFound(id.toString()));
  }

  public PaymentResponse processPayment(String idempotencyKey, PostPaymentRequest request) {

    var existing = paymentsRepository.getByIdempotencyKey(idempotencyKey);

    if (existing.isPresent()) {
      PaymentResponse existingPayment = existing.get();

      if (existingPayment.getStatus() != PaymentStatus.PENDING) {
        LOG.info(
            "Idempotent replay for key {}, status {}", idempotencyKey, existingPayment.getStatus());

        return existingPayment;
      }

      // this should be preceded by an inquiry/status call to check the payment state before
      // re-submitting, but the bank simulator is not designed to support this
      LOG.warn(
          "Retrying PENDING payment for key {}, retry count {}",
          idempotencyKey,
          existingPayment.getRetryCount() + 1);

      existingPayment.setRetryCount(existingPayment.getRetryCount() + 1);

      return callBankAndUpdateStatus(existingPayment, request);
    }

    List<String> validationErrors = PaymentRequestValidator.validate(request);

    if (!validationErrors.isEmpty()) {
      LOG.info("Payment rejected for key {}: {}", idempotencyKey, validationErrors);
      throw new EventProcessingException("invalid card data", validationErrors);
    }

    PaymentResponse payment = buildPaymentResponse(idempotencyKey, request);
    payment.setStatus(PaymentStatus.PENDING);
    paymentsRepository.addWithIdempotencyKey(idempotencyKey, payment);

    return callBankAndUpdateStatus(payment, request);
  }

  private PaymentResponse callBankAndUpdateStatus(
      PaymentResponse payment, PostPaymentRequest request) {
    BankSimulatorRequest bankRequest =
        BankSimulatorRequest.builder()
            .cardNumber(request.getCardNumber())
            .expiryDate(request.getExpiryDate())
            .currency(request.getCurrency())
            .amount(request.getAmount())
            .cvv(request.getCvv())
            .build();

    BankSimulatorResponse bankResponse = bankSimulatorClient.processPayment(bankRequest);

    //  To enhance retry logic, we could introduce a separate status (e.g. BANK_ERROR)
    //  to distinguish bank failures (503) from explicit declines, allowing targeted retries
    //  without risking double-charging on legitimately declined payments.

    //  Retries should use an idempotent bank API or an inquiry/status endpoint to check
    //  the payment state before re-submitting, but the bank simulator is not designed to support
    // that.
    if (Boolean.TRUE.equals(bankResponse.authorized())) {
      payment.setStatus(PaymentStatus.AUTHORIZED);
    } else {
      payment.setStatus(PaymentStatus.DECLINED);
    }

    return payment;
  }

  private PaymentResponse buildPaymentResponse(
      String idempotencyKey, PostPaymentRequest request) {
    String cardNumberLastFour = null;

    // Only store last 4 digits — full card number is never persisted
    if (request.getCardNumber() != null && request.getCardNumber().length() >= 4) {
      cardNumberLastFour = request.getCardNumber().substring(request.getCardNumber().length() - 4);
    }

    return PaymentResponse.builder()
        .id(UUID.randomUUID())
        .idempotencyKey(idempotencyKey)
        .retryCount(0)
        .expiryMonth(request.getExpiryMonth())
        .expiryYear(request.getExpiryYear())
        .currency(request.getCurrency())
        .amount(request.getAmount())
        .cardNumberLastFour(cardNumberLastFour)
        .build();
  }
}
