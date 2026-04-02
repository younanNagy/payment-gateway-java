package com.checkout.payment.gateway.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentRequestValidatorTest {

  private PostPaymentRequest validRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("22224053432488877"); // 17 char
    request.setExpiryMonth(12);
    request.setExpiryYear(YearMonth.now().getYear() + 1);
    request.setCurrency("GBP");
    request.setAmount(100L);
    request.setCvv("123");
    return request;
  }

  @Test
  void validRequestReturnsNoErrors() {
    List<String> errors = PaymentRequestValidator.validate(validRequest());
    assertTrue(errors.isEmpty());
  }

  // Card number validation

  @Test
  void nullCardNumberIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber(null);
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("Card number is required")));
  }

  @Test
  void emptyCardNumberIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("");
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("Card number is required")));
  }

  @Test
  void cardNumberTooShortIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("1234567890123"); // 13 chars
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("between 14 and 19")));
  }

  @Test
  void cardNumberTooLongIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("12345678901234567890"); // 20 chars
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("between 14 and 19")));
  }

  @Test
  void cardNumberWithNonNumericIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("2222ABCD43248877");
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("only numeric")));
  }

  // Expiry month validation

  @Test
  void expiryMonthZeroIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(0);
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("Expiry month")));
  }

  @Test
  void expiryMonthThirteenIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(13);
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("Expiry month")));
  }

  @Test
  void expiryMonthNegativeIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(-1);
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("Expiry month")));
  }

  // Expiry date (month+year) validation

  @Test
  void expiryInThePastIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(1);
    request.setExpiryYear(2020);
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("in the future")));
  }

  @Test
  void expiryThisMonthIsRejected() {
    PostPaymentRequest request = validRequest();
    YearMonth now = YearMonth.now();
    request.setExpiryMonth(now.getMonthValue());
    request.setExpiryYear(now.getYear());
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("in the future")));
  }

  // Currency validation

  @Test
  void nullCurrencyIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCurrency(null);
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("Currency is required")));
  }

  @Test
  void unsupportedCurrencyIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCurrency("JPY");
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("USD, GBP, EUR")));
  }

  @Test
  void usdCurrencyIsValid() {
    PostPaymentRequest request = validRequest();
    request.setCurrency("USD");
    assertTrue(PaymentRequestValidator.validate(request).isEmpty());
  }

  @Test
  void gbpCurrencyIsValid() {
    PostPaymentRequest request = validRequest();
    request.setCurrency("GBP");
    assertTrue(PaymentRequestValidator.validate(request).isEmpty());
  }

  @Test
  void eurCurrencyIsValid() {
    PostPaymentRequest request = validRequest();
    request.setCurrency("EUR");
    assertTrue(PaymentRequestValidator.validate(request).isEmpty());
  }

  // Amount validation

  @Test
  void nullAmountIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setAmount(null);
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("greater than zero")));
  }

  @Test
  void negativeAmountIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setAmount(-100L);
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("greater than zero")));
  }

  // CVV validation

  @Test
  void nullCvvIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCvv(null);
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("CVV is required")));
  }

  @Test
  void cvvTooShortIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCvv("12");
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("3 or 4")));
  }

  @Test
  void cvvTooLongIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCvv("12345");
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("3 or 4")));
  }

  @Test
  void cvvWithNonNumericIsRejected() {
    PostPaymentRequest request = validRequest();
    request.setCvv("12A");
    List<String> errors = PaymentRequestValidator.validate(request);
    assertTrue(errors.stream().anyMatch(e -> e.contains("only numeric")));
  }

  @Test
  void cvvThreeDigitsIsValid() {
    PostPaymentRequest request = validRequest();
    request.setCvv("123");
    assertTrue(PaymentRequestValidator.validate(request).isEmpty());
  }

  @Test
  void cvvFourDigitsIsValid() {
    PostPaymentRequest request = validRequest();
    request.setCvv("1234");
    assertTrue(PaymentRequestValidator.validate(request).isEmpty());
  }
}
