package juyeop.jpay.batch.recovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.core.SnowflakeIds;
import juyeop.jpay.common.event.ChargeCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
public class ChargeRecoveryTxService {

    private final JdbcTemplate paymentJdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChargeRecoveryTxService(
            @Qualifier("paymentJdbcTemplate") JdbcTemplate paymentJdbcTemplate,
            ObjectMapper objectMapper) {
        this.paymentJdbcTemplate = paymentJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional("paymentTransactionManager")
    public void completeCharge(long chargeId, long userId, long amount,
                               String transferRef, String bankResponseMeta) {
        Instant now = Instant.now();
        int updated = paymentJdbcTemplate.update("""
                UPDATE charges
                SET status = 'COMPLETED', transfer_ref = ?, bank_response_meta = ?, completed_at = ?
                WHERE id = ? AND status = 'PENDING'
                """,
                transferRef, bankResponseMeta, now, chargeId);

        if (updated == 0) {
            log.info("charge already processed, skipping: chargeId={}", chargeId);
            return;
        }

        paymentJdbcTemplate.update("""
                UPDATE user_balance SET balance = balance + ? WHERE user_id = ?
                """,
                amount, userId);

        long outboxId = SnowflakeIds.next();
        String payload = toJson(new ChargeCompletedEvent(chargeId, userId, amount, now));
        paymentJdbcTemplate.update("""
                INSERT INTO outbox_events (id, topic, payload, published, created_at)
                VALUES (?, ?, ?, 0, ?)
                """,
                outboxId, ChargeCompletedEvent.TOPIC, payload, now);
    }

    @Transactional("paymentTransactionManager")
    public void failCharge(long chargeId, String failureReason, String bankResponseMeta) {
        int updated = paymentJdbcTemplate.update("""
                UPDATE charges
                SET status = 'FAILED', failure_reason = ?, bank_response_meta = ?, completed_at = ?
                WHERE id = ? AND status = 'PENDING'
                """,
                failureReason, bankResponseMeta, Instant.now(), chargeId);

        if (updated == 0) {
            log.info("charge already processed, skipping: chargeId={}", chargeId);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}