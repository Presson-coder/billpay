# Written answers

## 1. Money left the customer's account, the bill shows unpaid, and our record says PENDING. What would I check, and in what order?

1. **Our own record and logs** for that `clientReference` / `paymentId`. I would make sure that I have the correct amount and account, date/time of the transaction and reasons for PENDING (timeout or some problem at the gateway side).
2. **The biller's side.** Verify if the biller/gateway has received and posted the payment via the status inquiry API or directly from their website.
3. **Callbacks.** Verify if the biller tried to send us a callback which was declined or missed for some reason.
4. **Fix the issue.** If the payment is already on the biller side – then mark it as SUCCESSFUL and ask the biller to post the payment on the bill. If they never got it – mark it as FAILED and reverse client's debited funds back according to the process.

I would never simply resend the payment, because that risks charging the customer twice.

## 2. Where should the gateway's API key live, and where should it never be?

It should be kept in secrets management (e.g., Hashicorp Vault, AWS Secrets Management or Kubernetes Secrets), or in environment variables passed at deployment time. Only the service should be able to access the secret, and it must be rotated on a regular basis.

The secret must not appear in source code, in Git (including `application.properties` files committed to the repository), in Docker containers, in logs/stack traces, or exchanged via email/chat.

## 3. What customer data would I mask or leave out of logs, and why?

- **Masking**: account number and phone number (MSISDN), with display limited to last 3-4 digits only. The masking of account number is done by this service currently and the phone number will not be logged.
- **No logging**: PINs, passwords, card numbers, API keys or tokens, or the entire request/response body if it contains any personal information.

Logs are disseminated widely and stored for an extensive period of time, so any log breach should not leak any personal customer information. It is also a legal requirement, as per the Cyber and Data Protection Act of Zimbabwe, personal data should be kept to the minimum necessary.

## 4. Why use BigDecimal instead of double for amounts?

The `double` data type represents binary floating point, so many decimal numbers cannot be represented accurately. For instance, 0.1+0.2 yields 0.30000000000000004, and such slight differences accumulate in the total and reconciliations.

`BigDecimal` represents the exact decimal number. In addition, it allows precise control of the scale (2 decimal places) and the rounding mode.

## 5. One improvement with another day

A **job to reconcile scheduled payments marked as PENDING**. It would periodically check the status of payments marked as PENDING after some predetermined period of time and mark them as either SUCCESSFUL or FAILED. After the expiration of some cutoff period, it would inform the support team.

The current problem is that a PENDING payment will be successful only when a callback is sent by the biller. This job will automate this process and detect most "money was debited from my account but the bill is not paid" issues before the user calls.
