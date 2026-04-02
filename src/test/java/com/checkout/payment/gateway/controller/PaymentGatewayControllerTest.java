package com.checkout.payment.gateway.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.BankSimulatorClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.BankSimulatorResponse;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private PaymentsRepository paymentsRepository;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private BankSimulatorClient bankSimulatorClient;

  private Map<String, Object> validRequestBody() {
    Map<String, Object> body = new HashMap<>();
    body.put("card_number", "2222405343248877");
    body.put("expiry_month", 12);
    body.put("expiry_year", YearMonth.now().getYear() + 1);
    body.put("currency", "GBP");
    body.put("amount", 100);
    body.put("cvv", "123");
    return body;
  }

  private BankSimulatorResponse authorizedResponse() {
    return BankSimulatorResponse.builder()
        .authorized(true)
        .authorizationCode(UUID.randomUUID().toString())
        .build();
  }

  private BankSimulatorResponse declinedResponse() {
    return BankSimulatorResponse.builder().authorized(false).authorizationCode("").build();
  }

  // ========== GET /payment/{id} ==========

  @Test
  void whenPaymentWithIdExistsThenCorrectPaymentIsReturned() throws Exception {
    PaymentResponse payment =
        PaymentResponse.builder()
            .id(UUID.randomUUID())
            .amount(10L)
            .currency("USD")
            .status(PaymentStatus.AUTHORIZED)
            .expiryMonth(12)
            .expiryYear(2025)
            .cardNumberLastFour("4321")
            .build();

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(payment.getId().toString()))
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThenNotFoundErrorIsReturned() throws Exception {
    UUID missingId = UUID.randomUUID();

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + missingId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value(containsString("Payment not found")))
        .andExpect(jsonPath("$.message").value(containsString(missingId.toString())));
  }

  // ========== POST /payment — Authorized ==========

  @Test
  void whenValidPaymentIsAuthorizedThen200IsReturned() throws Exception {
    Mockito.when(bankSimulatorClient.processPayment(Mockito.any()))
        .thenReturn(authorizedResponse());

    mvc.perform(
            MockMvcRequestBuilders.post("/payment")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequestBody())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"))
        .andExpect(jsonPath("$.expiryMonth").value(12))
        .andExpect(jsonPath("$.expiryYear").value(YearMonth.now().getYear() + 1))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.retryCount").value(0));
  }

  // ========== POST /payment — Declined ==========

  @Test
  void whenValidPaymentIsDeclinedThen200IsReturned() throws Exception {
    Mockito.when(bankSimulatorClient.processPayment(Mockito.any())).thenReturn(declinedResponse());

    mvc.perform(
            MockMvcRequestBuilders.post("/payment")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequestBody())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"));
  }

  // ========== POST /payment — Rejected (validation → ErrorResponse) ==========

  @Test
  void whenCardNumberIsInvalidThenRejectedWithErrors() throws Exception {
    Map<String, Object> body = validRequestBody();
    body.put("card_number", "123"); // too short

    mvc.perform(
            MockMvcRequestBuilders.post("/payment")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.paymentStatus").value("Rejected"))
        .andExpect(jsonPath("$.message").value("invalid card data"))
        .andExpect(jsonPath("$.errors", hasItem(containsString("between 14 and 19"))));
  }

  // ========== POST /payment — Idempotency-Key validation ==========

  @Test
  void whenIdempotencyKeyIsMissingThen400IsReturned() throws Exception {
    mvc.perform(
            MockMvcRequestBuilders.post("/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequestBody())))
        .andExpect(status().isBadRequest());
  }

  // ========== Idempotency ==========

  @Test
  void whenSameIdempotencyKeyIsUsedThenSameResponseIsReturned() throws Exception {
    Mockito.when(bankSimulatorClient.processPayment(Mockito.any()))
        .thenReturn(authorizedResponse());

    String idempotencyKey = UUID.randomUUID().toString();
    String body = objectMapper.writeValueAsString(validRequestBody());

    MvcResult first =
        mvc.perform(
                MockMvcRequestBuilders.post("/payment")
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Authorized"))
            .andReturn();

    MvcResult second =
        mvc.perform(
                MockMvcRequestBuilders.post("/payment")
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Authorized"))
            .andReturn();

    // Bank should only be called once
    Mockito.verify(bankSimulatorClient, Mockito.times(1)).processPayment(Mockito.any());

    // Same payment ID returned
    String firstId =
        objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
    String secondId =
        objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();
    assertThat(firstId, equalTo(secondId));
  }

  @Test
  void whenDifferentIdempotencyKeysAreUsedThenDifferentPaymentsAreCreated() throws Exception {
    Mockito.when(bankSimulatorClient.processPayment(Mockito.any()))
        .thenReturn(authorizedResponse());

    String body = objectMapper.writeValueAsString(validRequestBody());

    MvcResult first =
        mvc.perform(
                MockMvcRequestBuilders.post("/payment")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();

    MvcResult second =
        mvc.perform(
                MockMvcRequestBuilders.post("/payment")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();

    String firstId =
        objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
    String secondId =
        objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();
    assertThat(firstId, not(equalTo(secondId)));
  }

  // ========== Card number masking ==========

  @Test
  void whenPaymentIsProcessedThenOnlyLastFourDigitsAreReturned() throws Exception {
    Mockito.when(bankSimulatorClient.processPayment(Mockito.any()))
        .thenReturn(authorizedResponse());

    Map<String, Object> body = validRequestBody();
    body.put("card_number", "4111111111111111");

    mvc.perform(
            MockMvcRequestBuilders.post("/payment")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cardNumberLastFour").value("1111"));
  }
}
