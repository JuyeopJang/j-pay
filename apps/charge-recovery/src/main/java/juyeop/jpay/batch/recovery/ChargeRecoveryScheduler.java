package juyeop.jpay.batch.recovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class ChargeRecoveryScheduler {

    // 정상 충전 처리 시간(< 10초)보다 충분히 길게 — 이 기준보다 오래된 PENDING만 복구 대상으로 삼는다.
    private static final Duration RECOVERY_THRESHOLD = Duration.ofMinutes(3);

    private final JdbcTemplate paymentJdbcTemplate;
    private final RestClient bankMockRestClient;
    private final ChargeRecoveryTxService txService;
    private final ObjectMapper objectMapper;

    public ChargeRecoveryScheduler(
            @Qualifier("paymentJdbcTemplate") JdbcTemplate paymentJdbcTemplate,
            RestClient bankMockRestClient,
            ChargeRecoveryTxService txService,
            ObjectMapper objectMapper) {
        this.paymentJdbcTemplate = paymentJdbcTemplate;
        this.bankMockRestClient = bankMockRestClient;
        this.txService = txService;
        this.objectMapper = objectMapper;
    }

    record PendingCharge(long id, String externalId, long userId, long amount, String bankAccountId) {}
    record BankTransferRequest(long amount, String transferId, String bankAccountId) {}
    record BankTransferResponse(String transferRef, String errorCode, String message) {}

    @Scheduled(fixedDelay = 60_000)
    public void recover() {
        Instant threshold = Instant.now().minus(RECOVERY_THRESHOLD);
        List<PendingCharge> candidates = queryPendingCharges(threshold);
        if (candidates.isEmpty()) return;

        log.info("charge recovery: {} candidate(s) found", candidates.size());
        for (PendingCharge charge : candidates) {
            try {
                recoverOne(charge);
            } catch (Exception e) {
                log.warn("charge recovery failed: chargeId={}", charge.id(), e);
            }
        }
    }

    private List<PendingCharge> queryPendingCharges(Instant threshold) {
        return paymentJdbcTemplate.query("""
                SELECT id, external_id, user_id, amount, bank_account_id
                FROM charges
                WHERE status = 'PENDING' AND requested_at < ?
                """,
                (rs, rowNum) -> new PendingCharge(
                        rs.getLong("id"),
                        rs.getString("external_id"),
                        rs.getLong("user_id"),
                        rs.getLong("amount"),
                        rs.getString("bank_account_id")),
                threshold);
    }

    private void recoverOne(PendingCharge charge) {
        BankTransferResponse response;
        try {
            response = bankMockRestClient.post()
                    .uri("/internal/bank-mock/transfer")
                    .body(new BankTransferRequest(charge.amount(), charge.externalId(), charge.bankAccountId()))
                    .retrieve()
                    .body(BankTransferResponse.class);
        } catch (Exception e) {
            // 은행 API 자체가 불안정한 경우 — 이번 사이클 건너뛰고 다음 실행 때 재시도
            log.warn("bank API unavailable during recovery, skip: chargeId={}", charge.id(), e);
            return;
        }

        if (response == null) {
            log.warn("bank API returned null response, skip: chargeId={}", charge.id());
            return;
        }

        String meta = toJson(response);
        if (response.errorCode() == null) {
            txService.completeCharge(charge.id(), charge.userId(), charge.amount(), response.transferRef(), meta);
        } else {
            txService.failCharge(charge.id(), response.message(), meta);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize bank response", e);
        }
    }
}