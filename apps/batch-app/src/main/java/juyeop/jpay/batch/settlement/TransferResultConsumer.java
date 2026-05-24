package juyeop.jpay.batch.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.batch.entity.SettlementStatus;
import juyeop.jpay.batch.repository.SettlementRepository;
import juyeop.jpay.common.event.TransferCompletedEvent;
import juyeop.jpay.common.event.TransferFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferResultConsumer {

    private final SettlementRepository settlementRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {TransferCompletedEvent.TOPIC, TransferFailedEvent.TOPIC},
            groupId = "batch-app"
    )
    public void consume(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        if (TransferCompletedEvent.TOPIC.equals(topic)) {
            handleCompleted(objectMapper.readValue(message, TransferCompletedEvent.class));
        } else {
            handleFailed(objectMapper.readValue(message, TransferFailedEvent.class));
        }
    }

    private void handleCompleted(TransferCompletedEvent event) {
        settlementRepository.findByTransferExternalId(event.externalId())
                .ifPresent(s -> {
                    if (s.getStatus() != SettlementStatus.PENDING) return;
                    s.markTransferred();
                    settlementRepository.save(s);
                    log.info("Settlement TRANSFERRED: externalId={}", event.externalId());
                });
    }

    private void handleFailed(TransferFailedEvent event) {
        settlementRepository.findByTransferExternalId(event.externalId())
                .ifPresent(s -> {
                    if (s.getStatus() != SettlementStatus.PENDING) return;
                    s.markFailed();
                    settlementRepository.save(s);
                    log.warn("Settlement FAILED: externalId={}, reason={}", event.externalId(), event.failureReason());
                });
    }
}
