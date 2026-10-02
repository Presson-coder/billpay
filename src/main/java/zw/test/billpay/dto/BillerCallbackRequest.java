package zw.test.billpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BillerCallbackRequest(

        @NotBlank(message = "is required") String reference,

        @NotNull(message = "is required") @Pattern(regexp = "APPROVED|DECLINED", message = "must be APPROVED or DECLINED") String status,

        @Size(max = 64, message = "must be at most 64 characters") String gatewayRef,

        @Size(max = 255, message = "must be at most 255 characters") String message) {

    @JsonIgnore
    public boolean isApproved() {
        return "APPROVED".equals(status);
    }

    /**
     * Reported as field "gatewayRefProvided" when an approval arrives without a
     * gatewayRef.
     */
    @JsonIgnore
    @AssertTrue(message = "gatewayRef is required when status is APPROVED")
    public boolean isGatewayRefProvided() {
        return !isApproved() || (gatewayRef != null && !gatewayRef.isBlank());
    }
}
