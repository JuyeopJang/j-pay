package juyeop.jpay.ledger.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.event.UserTransferCompletedEvent;
import juyeop.jpay.ledger.service.LedgerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserTransferCompletedEventConsumer extends AbstractLedgerEventConsumer<UserTransferCompletedEvent> {

    private final LedgerService ledgerService;

    public UserTransferCompletedEventConsumer(ObjectMapper objectMapper, LedgerService ledgerService) {
        super(objectMapper);
        this.ledgerService = ledgerService;
    }

    @KafkaListener(topics = UserTransferCompletedEvent.TOPIC, concurrency = "4")
    public void consume(String payload) throws Exception {
        super.consume(payload, UserTransferCompletedEvent.class);
    }

    @Override
    protected void process(UserTransferCompletedEvent event) {
        ledgerService.record(event);
    }

    @Override
    protected long extractEntityId(UserTransferCompletedEvent event) {
        return event.transferId();
    }
}