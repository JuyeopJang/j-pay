package juyeop.jpay.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.payment.dto.ChargeRequest;
import juyeop.jpay.payment.dto.ChargeResponse;
import juyeop.jpay.payment.entity.ChargeStatus;
import juyeop.jpay.payment.service.ChargeFacadeService;
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

@WebMvcTest(ChargeController.class)
class ChargeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ChargeFacadeService chargeFacadeService;

    private static final String IDEM_KEY         = "test-idem-key-001";
    private static final String USER_ID          = "1";
    private static final String BANK_ACCOUNT_ID  = "bank-acc-1234567890123456";
    private static final long   AMOUNT           = 100_000_000L;

    @Test
    void charge_validRequest_returns201() throws Exception {
        ChargeResponse stub = new ChargeResponse(
                "100001", ChargeStatus.COMPLETED, AMOUNT,
                "TRANSFER-001", null, Instant.now(), Instant.now());
        given(chargeFacadeService.charge(any(), any(), any())).willReturn(stub);

        mockMvc.perform(post("/charges")
                        .header("Idempotency-Key", IDEM_KEY)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChargeRequest(AMOUNT, BANK_ACCOUNT_ID))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chargeId").value("100001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void charge_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/charges")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChargeRequest(AMOUNT, BANK_ACCOUNT_ID))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void charge_missingUserId_returns400() throws Exception {
        mockMvc.perform(post("/charges")
                        .header("Idempotency-Key", IDEM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChargeRequest(AMOUNT, BANK_ACCOUNT_ID))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void charge_amountZero_returns400() throws Exception {
        mockMvc.perform(post("/charges")
                        .header("Idempotency-Key", IDEM_KEY)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChargeRequest(0L, BANK_ACCOUNT_ID))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void charge_amountExceedsMax_returns400() throws Exception {
        mockMvc.perform(post("/charges")
                        .header("Idempotency-Key", IDEM_KEY)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChargeRequest(100_000_001L, BANK_ACCOUNT_ID))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void charge_blankBankAccountId_returns400() throws Exception {
        mockMvc.perform(post("/charges")
                        .header("Idempotency-Key", IDEM_KEY)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChargeRequest(AMOUNT, ""))))
                .andExpect(status().isBadRequest());
    }
}