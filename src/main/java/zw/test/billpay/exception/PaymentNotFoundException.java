package zw.test.billpay.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String paymentId) {
        super("Payment '" + paymentId + "' was not found");
    }
}
