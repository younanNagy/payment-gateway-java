package com.checkout.payment.gateway.exception;

import java.util.List;
import lombok.Getter;

@Getter
public class EventProcessingException extends RuntimeException {
  private final List<String> errors;

  public EventProcessingException(String message, List<String> errors) {
    super(message);
    this.errors = errors;
  }
}
