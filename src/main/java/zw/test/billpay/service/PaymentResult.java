package zw.test.billpay.service;

import zw.test.billpay.model.Payment;

public record PaymentResult(Payment payment, boolean created) {

    public static PaymentResult created(Payment payment) {
        return new PaymentResult(payment, true);
    }

    public static PaymentResult replayed(Payment payment) {
        return new PaymentResult(payment, false);
    }
}
