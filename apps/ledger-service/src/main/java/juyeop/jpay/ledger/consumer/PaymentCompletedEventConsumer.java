package juyeop.jpay.ledger.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.event.PaymentCompletedEvent;
import juyeop.jpay.ledger.service.LedgerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentCompletedEventConsumer extends AbstractLedgerEventConsumer<PaymentCompletedEvent> {

    private final LedgerService ledgerService;

    public PaymentCompletedEventConsumer(ObjectMapper objectMapper, LedgerService ledgerService) {
        super(objectMapper);
        this.ledgerService = ledgerService;
    }

    @KafkaListener(topics = PaymentCompletedEvent.TOPIC, concurrency = "4")
    public void consume(String payload) throws Exception {
        super.consume(payload, PaymentCompletedEvent.class);
    }

    @Override
    protected void process(PaymentCompletedEvent event) {
        ledgerService.record(event);
    }

    @Override
    protected long extractEntityId(PaymentCompletedEvent event) {
        return event.paymentId();
    }
}