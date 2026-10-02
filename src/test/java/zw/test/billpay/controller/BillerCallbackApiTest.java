package zw.test.billpay.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import zw.test.billpay.gateway.BillerGateway;
import zw.test.billpay.repository.PaymentRepository;

/**
 * biller callback updates PENDING payments only.
 */
@SpringBootTest(properties = "billpay.gateway.timeout=300ms")
@AutoConfigureMockMvc
class BillerCallbackApiTest {

    private static final String CALLBACK = "/api/v1/callbacks/biller";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository repository;

    @MockitoBean
    private BillerGateway gateway;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("APPROVED callback turns a PENDING payment SUCCESSFUL")
    void approvedCallbackCompletesPendingPayment() throws Exception {
        String paymentId = createPendingPayment("MOB-CB-0001");

        postCallback(callback(paymentId, "APPROVED", "GW-777"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.gatewayRef").value("GW-777"));

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId))
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"));
    }

    @Test
    @DisplayName("DECLINED callback turns a PENDING payment FAILED")
    void declinedCallbackFailsPendingPayment() throws Exception {
        String paymentId = createPendingPayment("MOB-CB-0002");

        postCallback(callback(paymentId, "DECLINED", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    @DisplayName("Callback for a payment that is already final is ignored")
    void callbackForFinalPaymentIsIgnored() throws Exception {
        String paymentId = createPendingPayment("MOB-CB-0003");
        postCallback(callback(paymentId, "APPROVED", "GW-777")).andExpect(status().isOk());

        postCallback(callback(paymentId, "DECLINED", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.gatewayRef").value("GW-777"));
    }

    @Test
    @DisplayName("Callback for an unknown reference returns 404")
    void callbackForUnknownPaymentIsNotFound() throws Exception {
        postCallback(callback("does-not-exist", "APPROVED", "GW-777"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("APPROVED callback without a gatewayRef returns 400")
    void approvedCallbackRequiresGatewayRef() throws Exception {
        String paymentId = createPendingPayment("MOB-CB-0004");

        postCallback(callback(paymentId, "APPROVED", null))
                .andExpect(status().isBadRequest());
    }

    private String createPendingPayment(String clientReference) throws Exception {
        when(gateway.pay(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("Gateway timeout"));
        Map<String, Object> request = Map.of(
                "clientReference", clientReference,
                "billerCode", "ZESA",
                "accountNumber", "04123456789",
                "amount", new BigDecimal("25.50"),
                "currency", "USD",
                "customerMsisdn", "263771234567");
        String body = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.paymentId");
    }

    private ResultActions postCallback(Map<String, Object> callback) throws Exception {
        return mockMvc.perform(post(CALLBACK)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(callback)));
    }

    /** HashMap because Map.of does not allow the null gatewayRef some tests need. */
    private static Map<String, Object> callback(String reference, String status, String gatewayRef) {
        Map<String, Object> callback = new HashMap<>();
        callback.put("reference", reference);
        callback.put("status", status);
        callback.put("gatewayRef", gatewayRef);
        return callback;
    }
}
