package zw.test.billpay.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zw.test.billpay.dto.ApiError;
import zw.test.billpay.dto.BillerCallbackRequest;
import zw.test.billpay.dto.PaymentResponse;
import zw.test.billpay.service.PaymentService;

/**
 * Receives asynchronous results from the biller for payments left PENDING.
 * In production this endpoint must verify the caller (see README, "Securing callbacks").
 */
@RestController
@RequestMapping("/api/v1/callbacks")
@Tag(name = "Biller callbacks", description = "Called by the biller, not by the mobile app")
public class BillerCallbackController {

    private final PaymentService paymentService;

    public BillerCallbackController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/biller")
    @Operation(summary = "Receive a biller result",
            description = "Settles a PENDING payment. A payment that is already final is left unchanged and returned as is.")
    @ApiResponse(responseCode = "200", description = "Current state of the payment")
    @ApiResponse(responseCode = "400", description = "Validation failed or body is not valid JSON",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No payment with this reference",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Payment was updated by another request at the same time",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PaymentResponse receiveBillerCallback(@Valid @RequestBody BillerCallbackRequest callback) {
        return PaymentResponse.from(paymentService.applyBillerCallback(callback));
    }
}
