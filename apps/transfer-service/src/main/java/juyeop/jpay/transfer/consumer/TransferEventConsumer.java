package juyeop.jpay.transfer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.event.TransferRequestedEvent;
import juyeop.jpay.transfer.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventConsumer {

    private final TransferService transferService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TransferRequestedEvent.TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) throws Exception {
        TransferRequestedEvent event = objectMapper.readValue(message, TransferRequestedEvent.class);
        transferService.execute(event);
    }
}
