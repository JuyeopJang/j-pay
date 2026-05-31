package juyeop.jpay.payment.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.payment.controller.TransferController;
import juyeop.jpay.payment.dto.TransferRequest;
import juyeop.jpay.payment.dto.TransferResponse;
import juyeop.jpay.payment.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
@AutoConfigureRestDocs
class TransferApiDocTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    TransferService transferService;

    @Test
    void transfer() throws Exception {
        given(transferService.transfer(any(), any()))
                .willReturn(new TransferResponse(1001L, 1002L, 20_000L, 80_000L));

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", "transfer-idem-20240101-001")
                        .header("X-User-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferRequest(1002L, 20_000L))))
                .andExpect(status().isCreated())
                .andDo(document("transfers/transfer",
                        requestHeaders(
                                headerWithName("Idempotency-Key")
                                        .description("멱등성 키. 동일 키로 재요청 시 이전 결과 반환"),
                                headerWithName("X-User-Id")
                                        .description("송금 요청 사용자 ID (송금인)")
                        ),
                        requestFields(
                                fieldWithPath("toUserId")
                                        .description("송금 받을 사용자 ID"),
                                fieldWithPath("amount")
                                        .description("송금 금액 (원 단위, 1 이상 10,000,000 이하)")
                        ),
                        responseFields(
                                fieldWithPath("fromUserId")
                                        .description("송금인 사용자 ID"),
                                fieldWithPath("toUserId")
                                        .description("수금인 사용자 ID"),
                                fieldWithPath("amount")
                                        .description("송금 금액"),
                                fieldWithPath("fromBalanceAfter")
                                        .description("송금 후 송금인 잔액")
                        )
                ));
    }
}