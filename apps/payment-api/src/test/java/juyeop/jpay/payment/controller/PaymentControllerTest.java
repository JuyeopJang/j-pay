package juyeop.jpay.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.payment.dto.PaymentRequest;
import juyeop.jpay.payment.dto.PaymentResponse;
import juyeop.jpay.payment.entity.PaymentStatus;
import juyeop.jpay.payment.service.PaymentFacadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean PaymentFacadeService paymentFacadeService;

    private static final String IDEM_KEY    = "test-idem-key-001";
    private static final String USER_ID     = "1";
    private static final String MERCHANT_ID = "merchant-A";
    private static final long   AMOUNT      = 100_000_000L;

    private PaymentResponse stubResponse() {
        return new PaymentResponse(
                "200001", PaymentStatus.COMPLETED, AMOUNT,
                MERCHANT_ID, Instant.now(), Instant.now(), null);
    }

    private String body(long amount, String merchantId) throws Exception {
        return objectMapper.writeValueAsString(new PaymentRequest(amount, merchantId));
    }

    // --- 정상 요청 (3 endpoints) ---

    @Test
    void payOptimistic_validRequest_returns201() throws Exception {
        given(paymentFacadeService.payOptimistic(any(), any(), any())).willReturn(stubResponse());
        mockMvc.perform(post("/payments/optimistic")
                        .header("Idempotency-Key", IDEM_KEY)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(AMOUNT, MERCHANT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value("200001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void payPessimistic_validRequest_returns201() throws Exception {
        given(paymentFacadeService.payPessimistic(any(), any(), any())).willReturn(stubResponse());
        mockMvc.perform(post("/payments/pessimistic")
                        .header("Idempotency-Key", IDEM_KEY)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(AMOUNT, MERCHANT_ID)))
                .andExpect(status().isCreated());
    }

    @Test
    void payRedisLock_validRequest_returns201() throws Exception {
        given(paymentFacadeService.payWithRedisLock(any(), any(), any())).willReturn(stubResponse());
        mockMvc.perform(post("/payments/redis-lock")
                        .header("Idempotency-Key", IDEM_KEY)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(AMOUNT, MERCHANT_ID)))
                .andExpect(status().isCreated());
    }

    // --- 헤더/유효성 검증 (optimistic 엔드포인트 기준) ---

    @Test
    void pay_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/payments/optimistic")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(AMOUNT, MERCHANT_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pay_missingUserId_returns400() throws Exception {
        mockMvc.perform(post("/payments/optimistic")
                        .header("Idempotency-Key", IDEM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(AMOUNT, MERCHANT_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pay_amountZero_returns400() throws Exception {
        mockMvc.perform(post("/payments/optimistic")
                        .header("Idempotency-Key", IDEM_KEY)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(0L, MERCHANT_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pay_amountExceedsMax_returns400() throws Exception {
        mockMvc.perform(post("/payments/optimistic")
                        .header("Idempotency-Key", IDEM_KEY)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(100_000_001L, MERCHANT_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pay_blankMerchantId_returns400() throws Exception {
        mockMvc.perform(post("/payments/optimistic")
                        .header("Idempotency-Key", IDEM_KEY)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(AMOUNT, "")))
                .andExpect(status().isBadRequest());
    }
}