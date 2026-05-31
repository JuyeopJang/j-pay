package juyeop.jpay.payment.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.payment.controller.ChargeController;
import juyeop.jpay.payment.dto.ChargeRequest;
import juyeop.jpay.payment.dto.ChargeResponse;
import juyeop.jpay.payment.entity.ChargeStatus;
import juyeop.jpay.payment.service.ChargeFacadeService;
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

@WebMvcTest(ChargeController.class)
@AutoConfigureRestDocs
class ChargeApiDocTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ChargeFacadeService chargeFacadeService;

    @Test
    void charge() throws Exception {
        given(chargeFacadeService.charge(any(), any(), any()))
                .willReturn(new ChargeResponse(
                        "7648291034567890",
                        ChargeStatus.COMPLETED,
                        50_000L,
                        "BANK-REF-20240101-001",
                        null,
                        Instant.parse("2024-01-01T09:00:00Z"),
                        Instant.parse("2024-01-01T09:00:01Z")));

        mockMvc.perform(post("/charges")
                        .header("Idempotency-Key", "charge-idem-20240101-001")
                        .header("X-User-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChargeRequest(50_000L, "1101234567890001"))))
                .andExpect(status().isCreated())
                .andDo(document("charges/charge",
                        requestHeaders(
                                headerWithName("Idempotency-Key")
                                        .description("멱등성 키. 동일 키로 재요청 시 이전 결과 반환 (네트워크 재시도 안전)"),
                                headerWithName("X-User-Id")
                                        .description("인증된 사용자 ID")
                        ),
                        requestFields(
                                fieldWithPath("amount")
                                        .description("충전 금액 (원 단위, 1 이상 100,000,000 이하)"),
                                fieldWithPath("bankAccountId")
                                        .description("출금 계좌번호 (16 ~ 128자)")
                        ),
                        responseFields(
                                fieldWithPath("chargeId")
                                        .description("충전 거래 ID (Snowflake ID)"),
                                fieldWithPath("status")
                                        .description("충전 상태: `PENDING` | `COMPLETED` | `FAILED`"),
                                fieldWithPath("amount")
                                        .description("충전 금액"),
                                fieldWithPath("transferRef")
                                        .description("은행 이체 참조번호 (COMPLETED 시 존재)").optional(),
                                fieldWithPath("failureReason")
                                        .description("실패 사유 (FAILED 시 존재)").optional(),
                                fieldWithPath("requestedAt")
                                        .description("요청 시각 (ISO 8601 UTC)"),
                                fieldWithPath("completedAt")
                                        .description("처리 완료 시각 (ISO 8601 UTC, COMPLETED/FAILED 시 존재)").optional()
                        )
                ));
    }
}
