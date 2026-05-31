package juyeop.jpay.payment.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.payment.controller.PaymentController;
import juyeop.jpay.payment.dto.PaymentRequest;
import juyeop.jpay.payment.dto.PaymentResponse;
import juyeop.jpay.payment.entity.PaymentStatus;
import juyeop.jpay.payment.service.PaymentFacadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@AutoConfigureRestDocs
class PaymentApiDocTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PaymentFacadeService paymentFacadeService;

    private static final PaymentResponse STUB = new PaymentResponse(
            "8291034567891234",
            PaymentStatus.COMPLETED,
            10_000L,
            "merchant-001",
            Instant.parse("2024-01-01T09:00:00Z"),
            Instant.parse("2024-01-01T09:00:00Z"),
            null);

    @Test
    void payPessimistic() throws Exception {
        given(paymentFacadeService.payPessimistic(any(), any(), any())).willReturn(STUB);

        mockMvc.perform(post("/payments/pessimistic")
                        .header("Idempotency-Key", "payment-idem-20240101-001")
                        .header("X-User-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentRequest(10_000L, "merchant-001"))))
                .andExpect(status().isCreated())
                .andDo(document("payments/pessimistic",
                        requestHeaders(
                                headerWithName("Idempotency-Key")
                                        .description("멱등성 키. 동일 키로 재요청 시 이전 결과 반환"),
                                headerWithName("X-User-Id")
                                        .description("인증된 사용자 ID")
                        ),
                        requestFields(
                                fieldWithPath("amount")
                                        .description("결제 금액 (원 단위, 1 이상 100,000,000 이하)"),
                                fieldWithPath("merchantId")
                                        .description("가맹점 ID")
                        ),
                        responseFields(
                                fieldWithPath("paymentId")
                                        .description("결제 거래 ID (Snowflake ID)"),
                                fieldWithPath("status")
                                        .description("결제 상태: `COMPLETED` | `FAILED`"),
                                fieldWithPath("amount")
                                        .description("결제 금액"),
                                fieldWithPath("merchantId")
                                        .description("가맹점 ID"),
                                fieldWithPath("requestedAt")
                                        .description("요청 시각 (ISO 8601 UTC)"),
                                fieldWithPath("completedAt")
                                        .description("처리 완료 시각 (ISO 8601 UTC)").optional(),
                                fieldWithPath("failureReason")
                                        .description("실패 사유 (FAILED 시 존재)").optional()
                        )
                ));
    }

    @Test
    void payOptimistic() throws Exception {
        given(paymentFacadeService.payOptimistic(any(), any(), any())).willReturn(STUB);

        mockMvc.perform(post("/payments/optimistic")
                        .header("Idempotency-Key", "payment-idem-20240101-002")
                        .header("X-User-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentRequest(10_000L, "merchant-001"))))
                .andExpect(status().isCreated())
                .andDo(document("payments/optimistic",
                        requestHeaders(
                                headerWithName("Idempotency-Key").description("멱등성 키"),
                                headerWithName("X-User-Id").description("인증된 사용자 ID")
                        ),
                        requestFields(
                                fieldWithPath("amount").description("결제 금액"),
                                fieldWithPath("merchantId").description("가맹점 ID")
                        ),
                        responseFields(
                                fieldWithPath("paymentId").description("결제 거래 ID"),
                                fieldWithPath("status").description("결제 상태"),
                                fieldWithPath("amount").description("결제 금액"),
                                fieldWithPath("merchantId").description("가맹점 ID"),
                                fieldWithPath("requestedAt").description("요청 시각"),
                                fieldWithPath("completedAt").description("처리 완료 시각").optional(),
                                fieldWithPath("failureReason").description("실패 사유").optional()
                        )
                ));
    }

    @Test
    void payWithRedisLock() throws Exception {
        given(paymentFacadeService.payWithRedisLock(any(), any(), any())).willReturn(STUB);

        mockMvc.perform(post("/payments/redis-lock")
                        .header("Idempotency-Key", "payment-idem-20240101-003")
                        .header("X-User-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentRequest(10_000L, "merchant-001"))))
                .andExpect(status().isCreated())
                .andDo(document("payments/redis-lock",
                        requestHeaders(
                                headerWithName("Idempotency-Key").description("멱등성 키"),
                                headerWithName("X-User-Id").description("인증된 사용자 ID")
                        ),
                        requestFields(
                                fieldWithPath("amount").description("결제 금액"),
                                fieldWithPath("merchantId").description("가맹점 ID")
                        ),
                        responseFields(
                                fieldWithPath("paymentId").description("결제 거래 ID"),
                                fieldWithPath("status").description("결제 상태"),
                                fieldWithPath("amount").description("결제 금액"),
                                fieldWithPath("merchantId").description("가맹점 ID"),
                                fieldWithPath("requestedAt").description("요청 시각"),
                                fieldWithPath("completedAt").description("처리 완료 시각").optional(),
                                fieldWithPath("failureReason").description("실패 사유").optional()
                        )
                ));
    }
}
