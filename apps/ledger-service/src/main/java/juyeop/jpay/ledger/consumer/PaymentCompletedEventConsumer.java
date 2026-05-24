package juyeop.jpay.ledger.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.event.PaymentCompletedEvent;
import juyeop.jpay.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedEventConsumer {

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = PaymentCompletedEvent.TOPIC)
    public void consume(String payload) throws Exception {
        PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
        ledgerService.recordPayment(event);
    }
}
