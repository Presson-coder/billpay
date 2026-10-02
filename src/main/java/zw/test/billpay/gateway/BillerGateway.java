package zw.test.billpay.gateway;

import java.math.BigDecimal;

public interface BillerGateway {
    GatewayResponse pay(String billerCode, String accountNumber,
            BigDecimal amount, String currency, String reference);
}
