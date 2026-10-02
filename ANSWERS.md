# Written answers

## 1. Money left the customer's account, the bill shows unpaid, and our record says PENDING. What would I check, and in what order?

1. **Our own record and logs** for that `clientReference` / `paymentId`. I would confirm the amount and account, when it was sent, and why it is PENDING (timeout or gateway error).
2. **The biller's side.** Ask the gateway or biller for the transaction status using our reference, through a status enquiry API or their portal. This tells me whether they actually received and applied the payment.
3. **Callbacks.** Check whether the biller sent a callback that we rejected or failed to process.
4. **Settle it.** If the biller has it, mark the payment SUCCESSFUL and ask the biller to post it to the bill. If they never received it, mark it FAILED and reverse the customer's debit through the agreed process. Escalate if it is still unclear.

I would never simply resend the payment, because that risks charging the customer twice.

## 2. Where should the gateway's API key live, and where should it never be?

It should live in a secrets manager (for example HashiCorp Vault, AWS Secrets Manager or Kubernetes Secrets), or in environment variables injected at deploy time. Only the service should be able to read it, and it should be rotated regularly.

It should never be in source code, in Git (including a committed `application.properties`), in Docker images, in logs or error messages, or shared over email or chat.

## 3. What customer data would I mask or leave out of logs, and why?

- **Mask**: account number and phone number (MSISDN), showing only the last 3–4 digits. This service already masks the account number and does not log the phone number.
- **Never log**: PINs, passwords, card numbers, API keys or tokens, or full request and response bodies that contain personal data.

Logs are copied widely and kept a long time, so a leaked log should not expose customers. This is also a legal requirement: Zimbabwe's Cyber and Data Protection Act requires personal data to be protected and kept to the minimum needed. A transaction id or reference is enough for tracing.

## 4. Why use BigDecimal instead of double for amounts?

`double` is binary floating point, so many decimal values cannot be stored exactly. `0.1 + 0.2` gives `0.30000000000000004`, and small errors like this build up in totals and reconciliations.

`BigDecimal` stores the exact decimal value. It also gives full control over scale (2 decimal places) and rounding mode, which is what money needs.

## 5. One improvement with another day

A **scheduled reconciliation job for PENDING payments**. Every few minutes it would ask the biller for the status of payments that have been PENDING longer than a set time, and mark them SUCCESSFUL or FAILED. After a cut-off it would alert the support team.

Right now a PENDING payment only settles if the biller sends a callback. This job would settle stuck payments automatically and catch most "money left my account but the bill is unpaid" complaints before the customer has to call.
