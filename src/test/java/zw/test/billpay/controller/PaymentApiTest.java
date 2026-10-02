package zw.test.billpay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import zw.test.billpay.gateway.BillerGateway;
import zw.test.billpay.gateway.GatewayResponse;
import zw.test.billpay.model.PaymentStatus;
import zw.test.billpay.repository.PaymentRepository;

/**
 * End-to-end API tests through MockMvc with a real H2 database and a mocked biller gateway.
 * The gateway timeout is shortened so the timeout scenario runs in well under a second.
 */
@SpringBootTest(properties = "billpay.gateway.timeout=300ms")
@AutoConfigureMockMvc
class PaymentApiTest {

    private static final String PAYMENTS = "/api/v1/payments";

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

    // ---- Task 1 & 2: making a payment and handling gateway outcomes ----

    @Test
    @DisplayName("Approved payment returns 201 and is SUCCESSFUL with the gateway reference")
    void successfulPayment() throws Exception {
        gatewayReturns(new GatewayResponse("APPROVED", "GW-123", "Payment accepted"));

        String body = postPayment(validRequest("MOB-20261001-0001"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(PAYMENTS + "/")))
                .andExpect(jsonPath("$.paymentId").isNotEmpty())
                .andExpect(jsonPath("$.clientReference").value("MOB-20261001-0001"))
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.gatewayRef").value("GW-123"))
                .andExpect(jsonPath("$.message").value("Payment accepted"))
                .andReturn().getResponse().getContentAsString();

        assertStoredStatus(JsonPath.read(body, "$.paymentId"), PaymentStatus.SUCCESSFUL);
    }

    @Test
    @DisplayName("Declined payment returns 201 and is FAILED with the biller's reason")
    void declinedPayment() throws Exception {
        gatewayReturns(new GatewayResponse("DECLINED", null, "Account not found at biller"));

        postPayment(validRequest("MOB-20261001-0002"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("Account not found at biller"))
                .andExpect(jsonPath("$.gatewayRef").doesNotExist());
    }

    @Test
    @DisplayName("Gateway that does not answer in time leaves the payment PENDING, not FAILED")
    void gatewayTimeoutLeavesPaymentPending() throws Exception {
        when(gateway.pay(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return new GatewayResponse("APPROVED", "GW-LATE", "Payment accepted");
        });

        String body = postPayment(validRequest("MOB-20261001-0003"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        assertStoredStatus(JsonPath.read(body, "$.paymentId"), PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("Gateway exception leaves the payment PENDING and no stack trace reaches the caller")
    void gatewayExceptionLeavesPaymentPending() throws Exception {
        when(gateway.pay(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("Gateway timeout"));

        String body = postPayment(validRequest("MOB-20261001-0004"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("RuntimeException");
    }

    // ---- Task 1: validation ----

    @Test
    @DisplayName("Invalid fields return 400 listing each bad field, and the gateway is not called")
    void invalidRequestIsRejected() throws Exception {
        Map<String, Object> request = validRequest("MOB-20261001-0005");
        request.put("accountNumber", "12ab");
        request.put("amount", BigDecimal.ZERO);
        request.put("currency", "EUR");
        request.put("customerMsisdn", "0771234567");

        postPayment(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(4)))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        containsInAnyOrder("accountNumber", "amount", "currency", "customerMsisdn")));

        verify(gateway, never()).pay(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Amount with more than 2 decimal places returns 400")
    void amountWithTooManyDecimalsIsRejected() throws Exception {
        Map<String, Object> request = validRequest("MOB-20261001-0006");
        request.put("amount", new BigDecimal("25.505"));

        postPayment(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
    }

    @Test
    @DisplayName("Empty body returns 400 with every required field listed")
    void missingFieldsAreRejected() throws Exception {
        postPayment(Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(6)));
    }

    @Test
    @DisplayName("Malformed JSON returns 400")
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post(PAYMENTS).contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---- Task 3: duplicate protection ----

    @Test
    @DisplayName("Repeat request with the same details returns 200 with the original payment and does not pay twice")
    void duplicateWithSameDetailsReturnsOriginal() throws Exception {
        gatewayReturns(new GatewayResponse("APPROVED", "GW-123", "Payment accepted"));
        Map<String, Object> request = validRequest("MOB-20261001-0007");

        String first = postPayment(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(first, "$.paymentId");

        postPayment(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"));

        verify(gateway, times(1)).pay(any(), any(), any(), any(), any());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Repeat request with different details returns 409 Conflict")
    void duplicateWithDifferentDetailsIsConflict() throws Exception {
        gatewayReturns(new GatewayResponse("APPROVED", "GW-123", "Payment accepted"));
        Map<String, Object> request = validRequest("MOB-20261001-0008");
        postPayment(request).andExpect(status().isCreated());

        request.put("amount", new BigDecimal("99.00"));

        postPayment(request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        verify(gateway, times(1)).pay(any(), any(), any(), any(), any());
    }

    // ---- Task 4: status lookup ----

    @Test
    @DisplayName("GET returns the payment's current details")
    void getPaymentReturnsDetails() throws Exception {
        gatewayReturns(new GatewayResponse("APPROVED", "GW-123", "Payment accepted"));
        String created = postPayment(validRequest("MOB-20261001-0009"))
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(created, "$.paymentId");

        mockMvc.perform(get(PAYMENTS + "/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.billerCode").value("ZESA"))
                .andExpect(jsonPath("$.accountNumber").value("04123456780"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.customerMsisdn").doesNotExist());
    }

    @Test
    @DisplayName("GET for an unknown payment returns 404")
    void unknownPaymentIsNotFound() throws Exception {
        mockMvc.perform(get(PAYMENTS + "/{id}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---- helpers ----

    private void gatewayReturns(GatewayResponse response) {
        when(gateway.pay(any(), any(), any(), any(), any())).thenReturn(response);
    }

    private ResultActions postPayment(Map<String, Object> request) throws Exception {
        return mockMvc.perform(post(PAYMENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private void assertStoredStatus(String paymentId, PaymentStatus expected) {
        assertThat(repository.findById(paymentId))
                .hasValueSatisfying(payment -> assertThat(payment.getStatus()).isEqualTo(expected));
    }

    private static Map<String, Object> validRequest(String clientReference) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("clientReference", clientReference);
        request.put("billerCode", "ZESA");
        request.put("accountNumber", "04123456780");
        request.put("amount", new BigDecimal("25.50"));
        request.put("currency", "USD");
        request.put("customerMsisdn", "263771234567");
        return request;
    }
}
