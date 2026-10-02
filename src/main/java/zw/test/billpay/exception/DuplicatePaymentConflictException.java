package zw.test.billpay.exception;

/**
 * A clientReference was reused with different payment details.
 */
public class DuplicatePaymentConflictException extends RuntimeException {

    public DuplicatePaymentConflictException(String clientReference) {
        super("clientReference '" + clientReference + "' was already used for a payment with different details");
    }
}
