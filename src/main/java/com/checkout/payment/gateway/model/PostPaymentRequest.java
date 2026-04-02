package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.Data;
import lombok.ToString;

@Data
public class PostPaymentRequest implements Serializable {

  @JsonProperty("card_number")
  @NotBlank
  private String cardNumber;

  @JsonProperty("expiry_month")
  @NotNull
  private Integer expiryMonth;

  @JsonProperty("expiry_year")
  @NotNull
  private Integer expiryYear;

  @NotNull private String currency;
  @NotNull private Long amount;

  @ToString.Exclude // avoid leaking CVV in logs
  @NotBlank
  private String cvv;

  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }
}
