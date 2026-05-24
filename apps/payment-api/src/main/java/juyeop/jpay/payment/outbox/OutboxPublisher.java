package juyeop.jpay.payment.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxPublishTxService outboxPublishTxService;

    @Scheduled(fixedDelay = 1000)
    @SchedulerLock(name = "outboxPublisher", lockAtMostFor = "PT30S", lockAtLeastFor = "PT1S")
    public void publish() {
        for (OutboxEvent outboxEvent : outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc()) {
            try {
                kafkaTemplate.send(outboxEvent.getTopic(), outboxEvent.getPayload()).get();
                outboxPublishTxService.markPublished(outboxEvent.getId());
            } catch (Exception e) {
                log.error("outbox publish failed: eventId={}", outboxEvent.getId(), e);
            }
        }
    }
}