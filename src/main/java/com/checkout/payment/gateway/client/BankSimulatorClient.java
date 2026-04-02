package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.model.BankSimulatorRequest;
import com.checkout.payment.gateway.model.BankSimulatorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class BankSimulatorClient {

  private static final Logger LOG = LoggerFactory.getLogger(BankSimulatorClient.class);

  private final RestTemplate restTemplate;
  private final String bankUrl;

  public BankSimulatorClient(
      RestTemplate restTemplate, @Value("${bank.simulator.url}") String bankUrl) {
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
  }

  /** Calls the acquiring bank to process the payment. Returns unauthorized on bank errors. */
  public BankSimulatorResponse processPayment(BankSimulatorRequest request) {
    try {
      ResponseEntity<BankSimulatorResponse> response =
          restTemplate.postForEntity(bankUrl, request, BankSimulatorResponse.class);
      return response.getBody();
    } catch (HttpServerErrorException e) {
      LOG.error("Bank returned server error: {} {}", e.getStatusCode(), e.getMessage());
      return BankSimulatorResponse.builder().authorized(false).build();
    } catch (RestClientException e) {
      LOG.error("Failed to communicate with bank: {}", e.getMessage());
      return BankSimulatorResponse.builder().authorized(false).build();
    }
  }
}
