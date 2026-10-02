package zw.test.billpay.gateway;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

@Component
public class SimulatedBillerGateway implements BillerGateway {
    @Override
    public GatewayResponse pay(String billerCode, String accountNumber,
            BigDecimal amount, String currency, String reference) {
        char last = accountNumber.charAt(accountNumber.length() - 1);
        if (last == '9') {
            try {
                Thread.sleep(6000);
            } catch (InterruptedException ignored) {
            }
            throw new RuntimeException("Gateway timeout");
        }
        if (last == '1') {
            return new GatewayResponse("DECLINED", null, "Account not found at biller");
        }
        return new GatewayResponse("APPROVED", "GW-" + System.nanoTime(), "Payment accepted");
    }
}
