package zw.test.billpay.model;

public enum PaymentStatus {
    PENDING(false),
    SUCCESSFUL(true),
    FAILED(true);

    private final boolean terminal;

    PaymentStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isFinal() {
        return terminal;
    }
}
