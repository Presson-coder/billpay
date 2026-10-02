package zw.test.billpay.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import zw.test.billpay.dto.BillerCallbackRequest;
import zw.test.billpay.dto.PaymentRequest;
import zw.test.billpay.exception.DuplicatePaymentConflictException;
import zw.test.billpay.exception.PaymentNotFoundException;
import zw.test.billpay.gateway.GatewayClient;
import zw.test.billpay.gateway.GatewayResult;
import zw.test.billpay.model.Payment;
import zw.test.billpay.repository.PaymentRepository;
import zw.test.billpay.util.Masking;

/**
 * Payment use cases. Deliberately not @Transactional: a database transaction must not stay open
 * while we wait on a slow external gateway, so each save commits on its own.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository repository;
    private final GatewayClient gatewayClient;

    public PaymentService(PaymentRepository repository, GatewayClient gatewayClient) {
        this.repository = repository;
        this.gatewayClient = gatewayClient;
    }

    /**
     * Creates a payment, or replays the original one if this clientReference was already seen.
     * The gateway is called at most once per clientReference.
     */
    public PaymentResult createPayment(PaymentRequest request) {
        Optional<Payment> existing = repository.findByClientReference(request.clientReference());
        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        // 1. Record the payment as PENDING before calling out, so it is never lost.
        Payment payment;
        try {
            payment = repository.saveAndFlush(toNewPayment(request));
        } catch (DataIntegrityViolationException e) {
            // A concurrent request with the same clientReference was saved first (unique constraint).
            Payment winner = repository.findByClientReference(request.clientReference()).orElseThrow(() -> e);
            return replay(winner, request);
        }
        log.info("Payment {} created: ref={}, biller={}, account={}, amount={} {}",
                payment.getPaymentId(), payment.getClientReference(), payment.getBillerCode(),
                Masking.mask(payment.getAccountNumber()), payment.getAmount(), payment.getCurrency());

        // 2. Call the biller (bounded by the configured timeout) and record the outcome.
        GatewayResult result = gatewayClient.pay(payment);
        return PaymentResult.created(recordOutcome(payment, result));
    }

    public Payment getPayment(String paymentId) {
        return repository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    /**
     * Applies the biller's final answer to a PENDING payment.
     * Callbacks for payments that are already final are ignored, so they can be safely repeated.
     */
    public Payment applyBillerCallback(BillerCallbackRequest callback) {
        Payment payment = getPayment(callback.reference());
        if (payment.isFinal()) {
            log.info("Ignoring {} callback for payment {}: already {}",
                    callback.status(), payment.getPaymentId(), payment.getStatus());
            return payment;
        }
        if (callback.isApproved()) {
            payment.markSuccessful(callback.gatewayRef(), "Payment confirmed by biller");
        } else {
            payment.markFailed(callback.message() == null || callback.message().isBlank()
                    ? "Payment declined by biller" : callback.message());
        }
        log.info("Payment {} updated by biller callback to {}", payment.getPaymentId(), payment.getStatus());
        return repository.saveAndFlush(payment);
    }

    private PaymentResult replay(Payment original, PaymentRequest request) {
        if (!hasSameDetails(original, request)) {
            log.warn("Rejected reuse of clientReference {} with different details", request.clientReference());
            throw new DuplicatePaymentConflictException(request.clientReference());
        }
        log.info("Duplicate request for clientReference {}; returning payment {} without calling the biller",
                request.clientReference(), original.getPaymentId());
        return PaymentResult.replayed(original);
    }

    private static boolean hasSameDetails(Payment payment, PaymentRequest request) {
        return payment.getBillerCode().equals(request.billerCode())
                && payment.getAccountNumber().equals(request.accountNumber())
                && payment.getAmount().compareTo(request.amount()) == 0 // 25.5 equals 25.50
                && payment.getCurrency().equals(request.currency())
                && payment.getCustomerMsisdn().equals(request.customerMsisdn());
    }

    private Payment recordOutcome(Payment payment, GatewayResult result) {
        switch (result.outcome()) {
            case APPROVED -> payment.markSuccessful(result.gatewayRef(), result.message());
            case DECLINED, NOT_SENT -> payment.markFailed(result.message());
            case UNKNOWN -> payment.markPending(result.message());
        }
        log.info("Payment {} is now {}: {}", payment.getPaymentId(), payment.getStatus(), payment.getStatusMessage());
        try {
            return repository.saveAndFlush(payment);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Someone else (e.g. a biller callback) updated it first; theirs is the newer truth.
            log.info("Payment {} was updated concurrently; returning the stored state", payment.getPaymentId());
            return repository.findById(payment.getPaymentId()).orElseThrow(() -> e);
        }
    }

    private static Payment toNewPayment(PaymentRequest request) {
        return Payment.initiate(request.clientReference(), request.billerCode(), request.accountNumber(),
                request.amount(), request.currency(), request.customerMsisdn());
    }
}
