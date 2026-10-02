package zw.test.billpay.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zw.test.billpay.dto.BillerCallbackRequest;
import zw.test.billpay.dto.PaymentResponse;
import zw.test.billpay.service.PaymentService;

/**
 * Receives asynchronous results from the biller for payments left PENDING.
 * In production this endpoint must verify the caller (see README, "Securing callbacks").
 */
@RestController
@RequestMapping("/api/v1/callbacks")
public class BillerCallbackController {

    private final PaymentService paymentService;

    public BillerCallbackController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/biller")
    public PaymentResponse receiveBillerCallback(@Valid @RequestBody BillerCallbackRequest callback) {
        return PaymentResponse.from(paymentService.applyBillerCallback(callback));
    }
}
