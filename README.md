# Template structure

src/ - A skeleton SpringBoot Application

test/ - Some simple JUnit tests

imposters/ - contains the bank simulator configuration. Don't change this

.editorconfig - don't change this. It ensures a consistent set of rules for submissions when reformatting code

docker-compose.yml - configures the bank simulator


# API Documentation
For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**

**Feel free to change the structure of the solution, use a different library etc.**

---

# Running the Service

**Requirements:** Java 17

## With Docker (recommended)

```bash
docker-compose up
```

This starts both the bank simulator and the payment gateway. The API is available at `http://localhost:8090`.

### Without Docker (local development)

Start the bank simulator separately:

```bash
docker-compose up bank_simulator
```

Then run the service:

```bash
./gradlew bootRun
```

## Running Tests

```bash
./gradlew test
```

Tests mock the bank simulator, so Docker is not required.

---

# Sample Requests

## Process a payment (authorized — odd-ending card number)

```bash
curl -X POST http://localhost:8090/payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-12345" \
  -d '{
    "card_number": "2222405343248877",
    "expiry_month": 12,
    "expiry_year": 2026,
    "currency": "GBP",
    "amount": 1050,
    "cvv": "123"
  }'
```

Response (`200 OK`):
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "Authorized",
  "cardNumberLastFour": "8877",
  "expiryMonth": 12,
  "expiryYear": 2026,
  "currency": "GBP",
  "amount": 1050,
  "retryCount": 0
}
```

## Process a payment (declined — even-ending card number)

```bash
curl -X POST http://localhost:8090/payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-67890" \
  -d '{
    "card_number": "2222405343248878",
    "expiry_month": 12,
    "expiry_year": 2026,
    "currency": "USD",
    "amount": 500,
    "cvv": "456"
  }'
```

Response (`200 OK`):
```json
{
  "id": "...",
  "status": "Declined",
  ...
}
```

## Process a payment (rejected — invalid card data)

```bash
curl -X POST http://localhost:8090/payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-bad" \
  -d '{
    "card_number": "123",
    "expiry_month": 13,
    "expiry_year": 2020,
    "currency": "JPY",
    "amount": 0,
    "cvv": "12"
  }'
```

Response (`400 Bad Request`):
```json
{
  "paymentStatus": "Rejected",
  "message": "invalid card data",
  "errors": [
    "Card number must be between 14 and 19 characters",
    "Expiry month must be between 1 and 12 and year must be greater than zero",
    "Currency must be one of: USD, GBP, EUR",
    "Amount must be greater than zero",
    "CVV must be 3 or 4 characters"
  ]
}
```

## Retrieve a payment

```bash
curl http://localhost:8090/payment/{id}
```

Response (`200 OK`):
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "Authorized",
  "cardNumberLastFour": "8877",
  "expiryMonth": 12,
  "expiryYear": 2026,
  "currency": "GBP",
  "amount": 1050,
  "retryCount": 0
}
```

## Retrieve a non-existent payment

```bash
curl http://localhost:8090/payment/00000000-0000-0000-0000-000000000000
```

Response (`404 Not Found`):
```json
{
  "paymentStatus": null,
  "message": "Payment not found: 00000000-0000-0000-0000-000000000000",
  "errors": null
}
```

---

# Design Decisions & Assumptions

## API Design

**POST /payment** processes a payment. Requires an `Idempotency-Key` header (must be non-blank).

**GET /payment/{id}** retrieves a previously processed payment by its UUID.

## Idempotency

Every POST request requires an `Idempotency-Key` header. The payment lifecycle follows a state machine:

```
PENDING -> AUTHORIZED | DECLINED
         (REJECTED if validation fails — no payment is created)
```

- On first request: the payment is stored with `PENDING` status before calling the bank, then updated with the bank's response.
- On replay (same idempotency key, terminal status): the stored response is returned without calling the bank again.
- On retry (same idempotency key, `PENDING` status): the bank is re-called and `retryCount` is incremented. This handles cases where a previous attempt failed mid-flight.

A `retryCount` field tracks how many times a given payment was retried.

## Validation

All request fields are validated at the gateway level before calling the bank. Validation is implemented as a static utility class (`PaymentRequestValidator`) with no Spring dependencies, making it easy to test in isolation.

| Field | Rules |
|-------|-------|
| Card number | Required, 14-19 characters, numeric only |
| Expiry month | Required, 1-12 |
| Expiry year | Required, month+year must be in the future |
| Currency | Required, one of USD, GBP, EUR |
| Amount | Required, positive integer (minor currency unit) |
| CVV | Required, 3-4 characters, numeric only |

Invalid requests throw an `EventProcessingException`, which is caught by a `@ControllerAdvice` handler and returned as a `400 Bad Request` with an `ErrorResponse` containing the `Rejected` status, a message, and a list of specific validation errors. The bank is never called for rejected payments.

## Error Handling

Errors are handled via exceptions and a centralized `@ControllerAdvice` exception handler:

| Exception | HTTP Status | Response |
|-----------|-------------|----------|
| `EventProcessingException` (validation) | 400 Bad Request | `ErrorResponse` with `Rejected` status and validation errors |
| `PaymentNotFound` | 404 Not Found | `ErrorResponse` with message |

## Bank Error Handling (503)

The `BankSimulatorClient` catches bank errors (503, network failures) and returns an unauthorized response rather than propagating exceptions. This means the service always receives a valid response and the payment is marked as `Declined`.

To enhance retry logic in production, a separate status (e.g. `BANK_ERROR`) could distinguish bank failures from explicit declines, allowing targeted retries without risking double-charging. Retries should use an idempotent bank API or an inquiry/status endpoint to check the payment state before re-submitting.

## Security Considerations

- **Card number storage**: Only the last 4 digits are persisted as a String. The full card number exists only in-memory during the request lifecycle and is never written to storage.
- **CVV**: Stored as String to preserve leading zeros. Excluded from `toString()` via `@ToString.Exclude` to prevent leaking in logs. Only held in-memory during processing.
- **Production considerations**: A real payment gateway would require PCI DSS compliance including TLS for all communication, tokenization or HSM-based encryption for card data at rest, and secure key management. These are out of scope for this exercise.

## Storage

An in-memory `HashMap` is used as the payment repository (as specified in the exercise). In production, this would be a database with ACID transactions to ensure the idempotency state machine is safe under concurrent requests.

## Testing

Tests use Mockito (`@MockBean`) to mock the `BankSimulatorClient`, so no Docker/bank simulator is needed to run them.