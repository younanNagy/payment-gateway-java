package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record BankSimulatorResponse(
    Boolean authorized, @JsonProperty("authorization_code") String authorizationCode) {}
