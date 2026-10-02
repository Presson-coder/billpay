package zw.test.billpay.gateway;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import zw.test.billpay.model.Payment;

@Component
public class GatewayClient {

    private static final Logger log = LoggerFactory.getLogger(GatewayClient.class);

    private final BillerGateway gateway;
    private final ExecutorService executor;
    private final Duration timeout;

    public GatewayClient(BillerGateway gateway,
            @Qualifier("gatewayExecutor") ExecutorService executor,
            GatewayProperties properties) {
        this.gateway = gateway;
        this.executor = executor;
        this.timeout = properties.timeout();
    }

    public GatewayResult pay(Payment payment) {
        Future<GatewayResponse> call;
        try {
            call = executor.submit(() -> gateway.pay(payment.getBillerCode(), payment.getAccountNumber(),
                    payment.getAmount(), payment.getCurrency(), payment.getPaymentId()));
        } catch (RejectedExecutionException e) {
            log.warn("Payment {} not sent: gateway call pool is full", payment.getPaymentId());
            return GatewayResult.notSent("Service is busy, payment was not sent to the biller. Please try again.");
        }
        return awaitResult(payment.getPaymentId(), call);
    }

    private GatewayResult awaitResult(String paymentId, Future<GatewayResponse> call) {
        try {
            return GatewayResult.from(call.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            call.cancel(true); // interrupt the hanging call and free the thread
            log.warn("Payment {}: no response from biller gateway within {} ms", paymentId, timeout.toMillis());
            return GatewayResult.unknown("No response from biller in time, awaiting confirmation");
        } catch (ExecutionException e) {
            log.warn("Payment {}: biller gateway call failed: {}", paymentId, e.getCause().toString());
            return GatewayResult.unknown("Biller gateway error, awaiting confirmation");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.cancel(true);
            log.warn("Payment {}: interrupted while waiting for biller gateway", paymentId);
            return GatewayResult.unknown("Interrupted while waiting for biller, awaiting confirmation");
        }
    }
}
