package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PaymentRequestValidator {

  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "GBP", "EUR");

  private PaymentRequestValidator() {}

  public static List<String> validate(PostPaymentRequest request) {
    List<String> errors = new ArrayList<>();

    validateCardNumber(request.getCardNumber(), errors);
    validateExpiryDate(request.getExpiryMonth(), request.getExpiryYear(), errors);
    validateCurrency(request.getCurrency(), errors);
    validateAmount(request.getAmount(), errors);
    validateCvv(request.getCvv(), errors);

    return errors;
  }

  private static void validateCardNumber(String cardNumber, List<String> errors) {
    if (cardNumber == null || cardNumber.isEmpty()) {
      errors.add("Card number is required");
      return;
    }
    if (cardNumber.length() < 14 || cardNumber.length() > 19) {
      errors.add("Card number must be between 14 and 19 characters");
    }
    if (!cardNumber.matches("\\d+")) {
      errors.add("Card number must contain only numeric characters");
    }
  }

  private static void validateExpiryDate(
      Integer expiryMonth, Integer expiryYear, List<String> errors) {

    if (expiryMonth == null
        || expiryYear == null
        || expiryMonth < 1
        || expiryMonth > 12
        || expiryYear < 1) {
      errors.add("Expiry month must be between 1 and 12 and year must be greater than zero");
      return;
    }

    YearMonth expiry = YearMonth.of(expiryYear, expiryMonth);
    if (!expiry.isAfter(YearMonth.now())) {
      errors.add("Card expiry date must be in the future");
    }
  }

  private static void validateCurrency(String currency, List<String> errors) {
    if (currency == null || currency.isEmpty()) {
      errors.add("Currency is required");
      return;
    }

    if (!SUPPORTED_CURRENCIES.contains(currency)) {
      errors.add("Currency must be one of: USD, GBP, EUR");
    }
  }

  private static void validateAmount(Long amount, List<String> errors) {
    if (amount == null || amount <= 0) {
      errors.add("Amount must be greater than zero");
    }
  }

  private static void validateCvv(String cvv, List<String> errors) {
    if (cvv == null || cvv.isEmpty()) {
      errors.add("CVV is required");
      return;
    }

    if (cvv.length() < 3 || cvv.length() > 4) {
      errors.add("CVV must be 3 or 4 characters");
    }

    if (!cvv.matches("\\d+")) {
      errors.add("CVV must contain only numeric characters");
    }
  }
}
