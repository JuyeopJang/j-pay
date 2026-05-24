package juyeop.jpay.batch.settlement;

import juyeop.jpay.batch.entity.SettlementOutboxEvent;
import juyeop.jpay.batch.repository.SettlementOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementOutboxPublisher {

    private final SettlementOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SettlementOutboxPublishTxService publishTxService;

    @Scheduled(fixedDelay = 1000)
    public void publish() {
        for (SettlementOutboxEvent event : outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc()) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload()).get();
                publishTxService.markPublished(event.getId());
            } catch (Exception e) {
                log.error("settlement outbox publish failed: eventId={}", event.getId(), e);
            }
        }
    }
}
