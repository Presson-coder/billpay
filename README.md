# BillPay Service

A REST API that lets the mobile app pay a bill through an external biller gateway, and later check whether the payment went through.

Built with Java 17+, Spring Boot 3.4, Spring Data JPA, H2 (in memory) and JUnit 5.

## Running

You need JDK 17 or 21 and Maven 3.9+.

```bash
mvn clean package          # compile, run all tests, build the jar
mvn spring-boot:run        # or: java -jar target/billpay-0.0.1-SNAPSHOT.jar
```

The service starts on `http://localhost:8080`. The H2 console is at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:billpay`, user `sa`, no password). Data is lost on restart.

Health check: `http://localhost:8080/actuator/health`.

### With Docker

```bash
docker compose up --build    # builds the image and starts the service on port 8080
docker compose down
```

The image is a multi-stage build: Maven builds the jar, and the runtime image is a slim JRE 21 running as a non-root user.

## Deployment (AWS ECS Fargate)

`.github/workflows/ci-cd.yml` runs on GitHub Actions:

| Trigger | What runs |
|---|---|
| Pull request | Build and tests, then a Docker build check (no push) |
| Push to `main` | Build and tests, then build the image, push it to ECR, and deploy it to ECS. The job waits until the service is stable |

The deploy job signs in to AWS through GitHub OIDC, so no AWS access keys are stored in GitHub. The task definition is in `.aws/task-definition.json`.

### One-time AWS setup

Create these once, by hand or with Terraform/CloudFormation:

1. **ECR repository** `billpay`.
2. **ECS cluster** `billpay-cluster` (Fargate).
3. **Task execution role** `billpayTaskExecutionRole`, with the `AmazonECSTaskExecutionRolePolicy` managed policy plus `logs:CreateLogGroup`.
4. **ECS service** `billpay-service`, running the `billpay` task definition behind an Application Load Balancer. Point the target group health check at `/actuator/health` on port 8080. Use **1 task** for now (see the note below).
5. **GitHub OIDC deploy role.** Add `token.actions.githubusercontent.com` as an IAM identity provider. Create a role that trusts `repo:Presson-coder/billpay:environment:production` and has permission to push to ECR, register task definitions, update the service, and `iam:PassRole` on the execution role.

### GitHub settings

Create an environment called `production`. You can add required reviewers to it as an approval gate. Then set:

| Kind | Name | Example |
|---|---|---|
| Secret | `AWS_DEPLOY_ROLE_ARN` | `arn:aws:iam::123456789012:role/github-billpay-deploy` |
| Variable | `AWS_ACCOUNT_ID` | `123456789012` |
| Variable | `AWS_REGION` | `af-south-1` |
| Variable (optional) | `ECR_REPOSITORY`, `ECS_CLUSTER`, `ECS_SERVICE` | defaults: `billpay`, `billpay-cluster`, `billpay-service` |

> **Note:** The database is still in-memory H2. In AWS each task has its own copy, and the data is lost on every deploy. Before running more than one task, or keeping real payments, move to a shared database such as RDS PostgreSQL.

## Running the tests

```bash
mvn test
```

The gateway is mocked in the tests and its timeout is cut to 300 ms, so the whole suite runs in seconds.

| Test class | What it covers |
|---|---|
| `GatewayClientTest` | Unit tests for the timeout wrapper: approved, declined, timeout, exception, unknown status, rejected call, and the real simulated gateway cut short for an account ending in 9 |
| `PaymentApiTest` | MockMvc + H2: successful, declined, timeout → PENDING, exception → PENDING, validation errors, malformed JSON, duplicate replay, 409 conflict, GET and 404 |
| `BillerCallbackApiTest` | Bonus callback: approve, decline, ignored when already final, 404, missing gatewayRef |

## API

### Make a payment: `POST /api/v1/payments`

The simulated gateway decides the outcome from the last digit of `accountNumber`:

| Account ends in | Gateway behaviour | Our status | HTTP |
|---|---|---|---|
| `1` | Declines | `FAILED` | 201 |
| `9` | Hangs 6 s, then errors | `PENDING` (after 3 s) | 201 |
| anything else | Approves | `SUCCESSFUL` | 201 |

```bash
# Approved
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"clientReference":"MOB-20261001-0001","billerCode":"ZESA","accountNumber":"04123456780","amount":25.50,"currency":"USD","customerMsisdn":"263771234567"}'

# Declined (account ends in 1)
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"clientReference":"MOB-20261001-0002","billerCode":"ZESA","accountNumber":"04123456781","amount":10.00,"currency":"USD","customerMsisdn":"263771234567"}'

# Timeout -> PENDING after about 3 seconds (account ends in 9)
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"clientReference":"MOB-20261001-0003","billerCode":"ZESA","accountNumber":"04123456789","amount":10.00,"currency":"ZWG","customerMsisdn":"263771234567"}'

# Validation error -> 400 listing each bad field
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"clientReference":"MOB-20261001-0004","billerCode":"ZESA","accountNumber":"12ab","amount":0,"currency":"EUR","customerMsisdn":"0771234567"}'
```

Example success response (`201 Created`, with a `Location` header):

```json
{
  "paymentId": "3f6c1e2a-9b1d-4a7e-8f51-2c0d9e7b4a10",
  "clientReference": "MOB-20261001-0001",
  "billerCode": "ZESA",
  "accountNumber": "04123456780",
  "amount": 25.50,
  "currency": "USD",
  "status": "SUCCESSFUL",
  "message": "Payment accepted",
  "gatewayRef": "GW-123456789",
  "createdAt": "2026-10-01T08:00:00.123Z",
  "updatedAt": "2026-10-01T08:00:00.456Z"
}
```

Example validation error (`400 Bad Request`):

```json
{
  "timestamp": "2026-10-01T08:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "fieldErrors": [
    { "field": "accountNumber", "message": "must be 6 to 20 digits" },
    { "field": "amount", "message": "must be greater than 0" },
    { "field": "currency", "message": "must be USD or ZWG" },
    { "field": "customerMsisdn", "message": "must start with 263 and have 12 digits" }
  ]
}
```

**Retries.** Sending the same `clientReference` again with the same details returns the original payment with `200 OK` and does not call the gateway. The same `clientReference` with different details returns `409 Conflict`.

### Check status: `GET /api/v1/payments/{paymentId}`

```bash
curl -i http://localhost:8080/api/v1/payments/<paymentId>
```

Returns `200` with the same body as above, or `404` with a JSON error for an unknown id.

### Bonus, biller callback: `POST /api/v1/callbacks/biller`

```bash
curl -i -X POST http://localhost:8080/api/v1/callbacks/biller \
  -H "Content-Type: application/json" \
  -d '{"reference":"<paymentId>","status":"APPROVED","gatewayRef":"GW-777"}'
```

`reference` is the `paymentId`, which is the reference we send to the gateway. The callback only changes a `PENDING` payment. For a payment that is already `SUCCESSFUL` or `FAILED` it is ignored and the current state is returned with `200`, so a biller repeating a callback is harmless. An unknown reference returns `404`.

## Design

```
controller/   HTTP only: PaymentController, BillerCallbackController
service/      Business rules: PaymentService interface (create, get, callback) and PaymentResult
service/impl/ PaymentServiceImpl (duplicate check, gateway call, outcome, callback)
gateway/      Biller integration: provided gateway + GatewayClient (timeout wrapper)
model/        Payment entity and PaymentStatus
repository/   Spring Data JPA repository
dto/          Request/response records and the JSON error body
exception/    Domain exceptions and the @RestControllerAdvice handler
```

Key decisions:

- **Never mark an unknown outcome as failed.** A timeout, an exception or an unrecognised gateway status leaves the payment `PENDING`, because the biller may already have taken the money. Only a clear `DECLINED` is `FAILED`. A call that never left our service (thread pool full) is also `FAILED`, since nothing reached the biller.
- **Hard 3-second limit.** `GatewayClient` submits the call to a dedicated, bounded thread pool and waits with `Future.get(3, SECONDS)`. On timeout it cancels the call, which interrupts the hanging thread and frees it. A slow biller cannot use up the web server's threads.
- **Save before and after the call.** The payment is stored as `PENDING` before the gateway is called, so nothing is lost if the service crashes mid-call. It is updated afterwards. No database transaction is held open while waiting on the gateway.
- **Duplicate safety in two layers.** The service looks up `clientReference` first. A database unique constraint backs this up: if two identical requests arrive at the same moment, the second insert fails and that request returns the first payment instead of paying twice.
- **Optimistic locking.** `@Version` on `Payment` stops a late gateway result and a callback from silently overwriting each other.
- **Money is `BigDecimal`**, stored as `DECIMAL(15,2)`.
- **The entity is never exposed.** Every endpoint returns `PaymentResponse`. The customer's phone number is not returned, and account numbers are masked in logs.
- **The gateway timeout is configurable**: `billpay.gateway.timeout` (default `3s`), plus `pool-size` and `queue-capacity`.

## Assumptions

- `paymentId` is a UUID and is the `reference` sent to the gateway and expected back in callbacks.
- `billerCode` is free text, up to 20 characters. There is no list of valid billers.
- A replayed duplicate returns the payment's current state, which may be `PENDING` if the first request is still waiting on the gateway.
- Amounts are compared by value when checking duplicates, so `25.5` and `25.50` count as the same.
- The H2 console is enabled for local inspection only. It would be off in any real environment.

## Securing callbacks (not implemented)

In production the callback endpoint must reject fake callbacks. I would use these together:

1. **HMAC signature.** The biller signs the raw body with a shared secret (for example an `X-Signature: sha256=...` header, plus a timestamp to stop replays). We recompute the signature and compare in constant time. The secret comes from a secrets store, never from code.
2. **IP allow-list and mutual TLS**, so only the biller's network or certificate can reach the endpoint.
3. **Cross-checking.** Only accept a callback whose reference matches a `PENDING` payment we actually sent, and optionally confirm with the biller's status enquiry before changing the status.

## Not done / next steps

- Callback authentication (described above).
- A scheduled job that re-queries the biller for payments stuck in `PENDING`, so they settle without a callback or a person.
- Retries with backoff for calls that never reached the biller.
- A persistent database (RDS PostgreSQL) for the AWS deployment, and infrastructure as code for the AWS resources.
- OpenAPI/Swagger docs, metrics on gateway latency and timeouts.
