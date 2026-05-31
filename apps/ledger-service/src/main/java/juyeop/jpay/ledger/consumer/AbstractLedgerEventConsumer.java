package juyeop.jpay.ledger.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractLedgerEventConsumer<T> {

    protected final ObjectMapper objectMapper;

    protected void consume(String payload, Class<T> eventType) throws Exception {
        T event = objectMapper.readValue(payload, eventType);
        try {
            process(event);
        } catch (DataIntegrityViolationException e) {
            // 동시 중복 이벤트: 다른 스레드가 먼저 처리 완료 → 재처리 불필요
            log.warn("Duplicate event skipped (concurrent race): entityId={}", extractEntityId(event));
        }
    }

    protected abstract void process(T event);

    protected abstract long extractEntityId(T event);
}