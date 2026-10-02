package zw.test.billpay.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Fields with a format rule use @NotNull rather than @NotBlank so each bad field reports one clear error.
 */
public record PaymentRequest(

        @NotBlank(message = "is required")
        @Size(max = 64, message = "must be at most 64 characters")
        String clientReference,

        @NotBlank(message = "is required")
        @Size(max = 20, message = "must be at most 20 characters")
        String billerCode,

        @NotNull(message = "is required")
        @Pattern(regexp = "\\d{6,20}", message = "must be 6 to 20 digits")
        String accountNumber,

        @NotNull(message = "is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "must be greater than 0")
        @Digits(integer = 13, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "is required")
        @Pattern(regexp = "USD|ZWG", message = "must be USD or ZWG")
        String currency,

        @NotNull(message = "is required")
        @Pattern(regexp = "263\\d{9}", message = "must start with 263 and have 12 digits")
        String customerMsisdn) {
}
