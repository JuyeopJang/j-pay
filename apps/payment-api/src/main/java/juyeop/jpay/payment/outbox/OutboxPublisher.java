package juyeop.jpay.payment.outbox;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxPublishTxService outboxPublishTxService;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           @Nullable KafkaTemplate<String, String> kafkaTemplate,
                           OutboxPublishTxService outboxPublishTxService) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxPublishTxService = outboxPublishTxService;
    }

    @Scheduled(fixedDelay = 100)
    @SchedulerLock(name = "outboxPublisher", lockAtMostFor = "PT30S", lockAtLeastFor = "PT0.1S")
    public void publish() {
        if (kafkaTemplate == null) return;

        List<OutboxEvent> events = outboxEventRepository.findTop500ByPublishedFalseOrderByCreatedAtAsc();
        if (events.isEmpty()) return;

        List<Long> successIds = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (OutboxEvent event : events) {
            long eventId = event.getId();
            CompletableFuture<?> future = kafkaTemplate.send(event.getTopic(), event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            successIds.add(eventId);
                        } else {
                            log.error("outbox publish failed: eventId={}", eventId, ex);
                        }
                    });
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (!successIds.isEmpty()) {
            outboxPublishTxService.markPublishedBatch(successIds);
        }
    }
}