package zw.test.billpay.controller;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zw.test.billpay.dto.ApiError;
import zw.test.billpay.dto.PaymentRequest;
import zw.test.billpay.dto.PaymentResponse;
import zw.test.billpay.service.PaymentResult;
import zw.test.billpay.service.PaymentService;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Make a bill payment and check its status")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 201 for a new payment, 200 when a retried request returns the original payment. */
    @PostMapping
    @Operation(summary = "Make a payment",
            description = "Sends the payment to the biller and waits at most 3 seconds. A timeout or unclear answer "
                    + "leaves the payment PENDING. Retrying with the same clientReference returns the original payment.")
    @ApiResponse(responseCode = "201", description = "Payment created")
    @ApiResponse(responseCode = "200", description = "Retried request; the original payment is returned")
    @ApiResponse(responseCode = "400", description = "Validation failed or body is not valid JSON",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "clientReference already used with different payment details",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResult result = paymentService.createPayment(request);
        PaymentResponse body = PaymentResponse.from(result.payment());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        URI location = URI.create("/api/v1/payments/" + body.paymentId());
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Check payment status")
    @ApiResponse(responseCode = "200", description = "Payment found")
    @ApiResponse(responseCode = "404", description = "No payment with this id",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PaymentResponse getPayment(
            @Parameter(description = "paymentId returned when the payment was made") @PathVariable String paymentId) {
        return PaymentResponse.from(paymentService.getPayment(paymentId));
    }
}
