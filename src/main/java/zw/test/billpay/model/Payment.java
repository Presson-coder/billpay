package zw.test.billpay.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * A bill payment as stored in the database.
 * State changes go through the mark* methods so a final payment can never be changed again.
 */
@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payments_client_reference",
                columnNames = "client_reference"))
public class Payment {

    private static final String AWAITING_BILLER = "Awaiting biller response";

    @Id
    @Column(name = "payment_id", length = 36, nullable = false, updatable = false)
    private String paymentId;

    @Column(name = "client_reference", length = 64, nullable = false, updatable = false)
    private String clientReference;

    @Column(name = "biller_code", length = 20, nullable = false, updatable = false)
    private String billerCode;

    @Column(name = "account_number", length = 20, nullable = false, updatable = false)
    private String accountNumber;

    @Column(name = "amount", precision = 15, scale = 2, nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    private String currency;

    @Column(name = "customer_msisdn", length = 12, nullable = false, updatable = false)
    private String customerMsisdn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 12, nullable = false)
    private PaymentStatus status;

    @Column(name = "status_message", length = 255)
    private String statusMessage;

    @Column(name = "gateway_ref", length = 64)
    private String gatewayRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** stops a late gateway result and a biller callback from overwriting each other. */
    @Version
    @Column(name = "version")
    private Long version;

    protected Payment() {

    }

    public static Payment initiate(String clientReference, String billerCode, String accountNumber,
                                   BigDecimal amount, String currency, String customerMsisdn) {
        Payment payment = new Payment();
        payment.paymentId = UUID.randomUUID().toString();
        payment.clientReference = clientReference;
        payment.billerCode = billerCode;
        payment.accountNumber = accountNumber;
        payment.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        payment.currency = currency;
        payment.customerMsisdn = customerMsisdn;
        payment.status = PaymentStatus.PENDING;
        payment.statusMessage = AWAITING_BILLER;
        return payment;
    }

    public void markSuccessful(String gatewayRef, String message) {
        requireNotFinal();
        this.status = PaymentStatus.SUCCESSFUL;
        this.gatewayRef = gatewayRef;
        this.statusMessage = message;
    }

    public void markFailed(String reason) {
        requireNotFinal();
        this.status = PaymentStatus.FAILED;
        this.statusMessage = reason;
    }

    /** Outcome unknown: stay PENDING so the payment is confirmed later, never failed blindly. */
    public void markPending(String reason) {
        requireNotFinal();
        this.status = PaymentStatus.PENDING;
        this.statusMessage = reason;
    }

    public boolean isFinal() {
        return status.isFinal();
    }

    private void requireNotFinal() {
        if (isFinal()) {
            throw new IllegalStateException("Payment " + paymentId + " is already " + status);
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getClientReference() {
        return clientReference;
    }

    public String getBillerCode() {
        return billerCode;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCustomerMsisdn() {
        return customerMsisdn;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public String getGatewayRef() {
        return gatewayRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
