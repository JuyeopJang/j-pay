package juyeop.jpay.ledger.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.event.ChargeCompletedEvent;
import juyeop.jpay.ledger.service.LedgerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ChargeCompletedEventConsumer extends AbstractLedgerEventConsumer<ChargeCompletedEvent> {

    private final LedgerService ledgerService;

    public ChargeCompletedEventConsumer(ObjectMapper objectMapper, LedgerService ledgerService) {
        super(objectMapper);
        this.ledgerService = ledgerService;
    }

    @KafkaListener(topics = ChargeCompletedEvent.TOPIC, concurrency = "4")
    public void consume(String payload) throws Exception {
        super.consume(payload, ChargeCompletedEvent.class);
    }

    @Override
    protected void process(ChargeCompletedEvent event) {
        ledgerService.record(event);
    }

    @Override
    protected long extractEntityId(ChargeCompletedEvent event) {
        return event.chargeId();
    }
}