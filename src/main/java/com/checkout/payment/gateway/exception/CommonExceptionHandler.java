package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(PaymentNotFound.class)
  public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFound ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(
        ErrorResponse.builder().message(ex.getMessage()).build(), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleInvalidRequestException(EventProcessingException ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(
        ErrorResponse.builder()
            .paymentStatus(PaymentStatus.REJECTED)
            .message(ex.getMessage())
            .errors(ex.getErrors())
            .build(),
        HttpStatus.BAD_REQUEST);
  }
}
