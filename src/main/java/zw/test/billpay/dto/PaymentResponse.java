package zw.test.billpay.dto;

import java.math.BigDecimal;
import java.time.Instant;

import zw.test.billpay.model.Payment;
import zw.test.billpay.model.PaymentStatus;

/**
 * API view of a payment, used by every endpoint so the JPA entity is never exposed.
 * The customer's phone number is deliberately left out for security
 */
public record PaymentResponse(
        String paymentId,
        String clientReference,
        String billerCode,
        String accountNumber,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String message,
        String gatewayRef,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getClientReference(),
                payment.getBillerCode(),
                payment.getAccountNumber(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getStatusMessage(),
                payment.getGatewayRef(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
