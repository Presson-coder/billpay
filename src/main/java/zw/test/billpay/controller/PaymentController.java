package zw.test.billpay.controller;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zw.test.billpay.dto.PaymentRequest;
import zw.test.billpay.dto.PaymentResponse;
import zw.test.billpay.service.PaymentResult;
import zw.test.billpay.service.PaymentService;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 201 for a new payment, 200 when a retried request returns the original payment. */
    @PostMapping
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
    public PaymentResponse getPayment(@PathVariable String paymentId) {
        return PaymentResponse.from(paymentService.getPayment(paymentId));
    }
}
