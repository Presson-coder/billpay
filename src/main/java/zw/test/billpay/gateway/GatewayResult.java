package zw.test.billpay.gateway;

public record GatewayResult(Outcome outcome, String gatewayRef, String message) {

    public enum Outcome {
        APPROVED,
        DECLINED,
        UNKNOWN,
        NOT_SENT
    }

    static GatewayResult from(GatewayResponse response) {
        if (response == null || response.status() == null) {
            return unknown("Empty response from biller gateway, awaiting confirmation");
        }
        return switch (response.status()) {
            case "APPROVED" -> new GatewayResult(Outcome.APPROVED, response.gatewayRef(),
                    messageOrDefault(response.message(), "Payment accepted"));
            case "DECLINED" -> new GatewayResult(Outcome.DECLINED, null,
                    messageOrDefault(response.message(), "Payment declined by biller"));
            default -> unknown("Unrecognised biller status '" + response.status() + "', awaiting confirmation");
        };
    }

    static GatewayResult unknown(String message) {
        return new GatewayResult(Outcome.UNKNOWN, null, message);
    }

    static GatewayResult notSent(String message) {
        return new GatewayResult(Outcome.NOT_SENT, null, message);
    }

    private static String messageOrDefault(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}
