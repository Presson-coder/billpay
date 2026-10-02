package zw.test.billpay.service;

import zw.test.billpay.model.Payment;

/**
 * Result of a create request: either a newly created payment or the original one replayed
 * for a retried request with the same clientReference.
 */
public record PaymentResult(Payment payment, boolean created) {

    public static PaymentResult created(Payment payment) {
        return new PaymentResult(payment, true);
    }

    public static PaymentResult replayed(Payment payment) {
        return new PaymentResult(payment, false);
    }
}
