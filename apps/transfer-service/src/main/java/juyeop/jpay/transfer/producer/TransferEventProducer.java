package juyeop.jpay.transfer.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.event.TransferCompletedEvent;
import juyeop.jpay.common.event.TransferFailedEvent;
import juyeop.jpay.transfer.entity.Transfer;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class TransferEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishCompleted(Transfer transfer) {
        TransferCompletedEvent event = new TransferCompletedEvent(
                transfer.getExternalId(),
                transfer.getMerchantId(),
                transfer.getAmount().amount(),
                transfer.getTransferRef(),
                Instant.now());
        send(TransferCompletedEvent.TOPIC, transfer.getExternalId(), event);
    }

    public void publishFailed(Transfer transfer) {
        TransferFailedEvent event = new TransferFailedEvent(
                transfer.getExternalId(),
                transfer.getMerchantId(),
                transfer.getAmount().amount(),
                transfer.getFailureReason(),
                Instant.now());
        send(TransferFailedEvent.TOPIC, transfer.getExternalId(), event);
    }

    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
