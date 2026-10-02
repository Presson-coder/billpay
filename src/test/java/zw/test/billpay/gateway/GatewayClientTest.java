package zw.test.billpay.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import zw.test.billpay.gateway.GatewayResult.Outcome;
import zw.test.billpay.model.Payment;

/**
 * Unit tests for the timeout and error handling around the gateway. No Spring context needed.
 */
class GatewayClientTest {

    private static final Duration TIMEOUT = Duration.ofMillis(200);
    private static final long MAX_EXPECTED_WAIT_MS = 2_000;

    private final BillerGateway gateway = mock(BillerGateway.class);
    private ExecutorService executor;
    private GatewayClient client;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        client = new GatewayClient(gateway, executor, new GatewayProperties(TIMEOUT, 2, 10));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("APPROVED response maps to APPROVED with the gateway reference")
    void approvedResponse() {
        gatewayReturns(new GatewayResponse("APPROVED", "GW-123", "Payment accepted"));

        GatewayResult result = client.pay(paymentForAccount("04123456780"));

        assertThat(result.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(result.gatewayRef()).isEqualTo("GW-123");
    }

    @Test
    @DisplayName("DECLINED response maps to DECLINED with the biller's reason")
    void declinedResponse() {
        gatewayReturns(new GatewayResponse("DECLINED", null, "Account not found at biller"));

        GatewayResult result = client.pay(paymentForAccount("04123456781"));

        assertThat(result.outcome()).isEqualTo(Outcome.DECLINED);
        assertThat(result.message()).isEqualTo("Account not found at biller");
    }

    @Test
    @DisplayName("Slow gateway is abandoned after the timeout and treated as UNKNOWN")
    void slowGatewayTimesOut() {
        when(gateway.pay(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return new GatewayResponse("APPROVED", "GW-LATE", "Payment accepted");
        });

        long start = System.nanoTime();
        GatewayResult result = client.pay(paymentForAccount("04123456780"));
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(elapsedMs).isLessThan(MAX_EXPECTED_WAIT_MS);
    }

    @Test
    @DisplayName("Gateway exception is treated as UNKNOWN, not as a failure")
    void gatewayExceptionIsUnknown() {
        when(gateway.pay(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("Connection reset"));

        GatewayResult result = client.pay(paymentForAccount("04123456780"));

        assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    @DisplayName("Unrecognised gateway status is treated as UNKNOWN")
    void unrecognisedStatusIsUnknown() {
        gatewayReturns(new GatewayResponse("PROCESSING", null, "Queued"));

        GatewayResult result = client.pay(paymentForAccount("04123456780"));

        assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    @DisplayName("Call refused by a full pool is NOT_SENT, since the biller never saw it")
    void rejectedCallIsNotSent() {
        executor.shutdown(); // any new submission is now rejected

        GatewayResult result = client.pay(paymentForAccount("04123456780"));

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_SENT);
    }

    @Test
    @DisplayName("Real simulated gateway: account ending in 9 returns UNKNOWN without waiting 6 seconds")
    void simulatedGatewayHangIsCutShort() {
        GatewayClient realClient = new GatewayClient(new SimulatedBillerGateway(), executor,
                new GatewayProperties(TIMEOUT, 2, 10));

        long start = System.nanoTime();
        GatewayResult result = realClient.pay(paymentForAccount("04123456789"));
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(elapsedMs).isLessThan(MAX_EXPECTED_WAIT_MS);
    }

    private void gatewayReturns(GatewayResponse response) {
        when(gateway.pay(any(), any(), any(), any(), any())).thenReturn(response);
    }

    private static Payment paymentForAccount(String accountNumber) {
        return Payment.initiate("MOB-TEST-0001", "ZESA", accountNumber,
                new BigDecimal("25.50"), "USD", "263771234567");
    }
}
