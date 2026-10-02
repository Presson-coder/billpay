package zw.test.billpay.service;

import zw.test.billpay.dto.BillerCallbackRequest;
import zw.test.billpay.dto.PaymentRequest;
import zw.test.billpay.model.Payment;

public interface PaymentService {

    PaymentResult createPayment(PaymentRequest request);

    Payment getPayment(String paymentId);

    Payment applyBillerCallback(BillerCallbackRequest callback);
}
