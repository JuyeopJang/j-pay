package juyeop.jpay.ledger.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.event.ChargeCompletedEvent;
import juyeop.jpay.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChargeCompletedEventConsumer {

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = ChargeCompletedEvent.TOPIC, concurrency = "4")
    public void consume(String payload) throws Exception {
        ChargeCompletedEvent event = objectMapper.readValue(payload, ChargeCompletedEvent.class);
        ledgerService.recordCharge(event);
    }
}
