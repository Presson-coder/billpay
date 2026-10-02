package zw.test.billpay.gateway;

public record GatewayResponse(String status, String gatewayRef, String message) {
}
