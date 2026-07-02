package juyeop.jpay.transfer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.event.TransferRequestedEvent;
import juyeop.jpay.transfer.AbstractTransferKafkaIntegrationTest;
import juyeop.jpay.transfer.entity.TransferStatus;
import juyeop.jpay.transfer.external.ExternalBankClient;
import juyeop.jpay.transfer.external.ExternalBankException;
import juyeop.jpay.transfer.external.dto.ExternalBankTransferResult;
import juyeop.jpay.transfer.producer.TransferEventProducer;
import juyeop.jpay.transfer.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.snowflake.node-id=99"
        }
)
class TransferConsumerKafkaTest extends AbstractTransferKafkaIntegrationTest {

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired TransferRepository transferRepository;

    @MockitoBean ExternalBankClient externalBankClient;
    @MockitoBean TransferEventProducer transferEventProducer;

    private static final String EXTERNAL_ID  = "SETTLE-merchant-1-20240101";
    private static final String MERCHANT_ID  = "merchant-1";
    private static final String BANK_ACCOUNT = "bank-acc-001";
    private static final long   AMOUNT       = 100_000L;

    @BeforeEach
    void setUp() {
        transferRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("transfer.requested 수신 + 은행 성공 → Transfer COMPLETED")
    void transferRequested_bankSucceeds_transferCompleted() throws Exception {
        given(externalBankClient.transfer(any()))
                .willReturn(new ExternalBankTransferResult.Succeeded("BANK-REF-001", Map.of()));

        TransferRequestedEvent event = new TransferRequestedEvent(EXTERNAL_ID, MERCHANT_ID, BANK_ACCOUNT, AMOUNT, Instant.now());
        kafkaTemplate.send(TransferRequestedEvent.TOPIC, objectMapper.writeValueAsString(event));

        await().atMost(10, SECONDS)
               .pollInterval(Duration.ofMillis(300))
               .untilAsserted(() -> {
                   var transfer = transferRepository.findByExternalId(EXTERNAL_ID);
                   assertThat(transfer).isPresent();
                   assertThat(transfer.get().getStatus()).isEqualTo(TransferStatus.COMPLETED);
                   assertThat(transfer.get().getTransferRef()).isEqualTo("BANK-REF-001");
               });
    }

    @Test
    @DisplayName("transfer.requested 수신 + 은행 실패 → Transfer FAILED")
    void transferRequested_bankFails_transferFailed() throws Exception {
        given(externalBankClient.transfer(any()))
                .willThrow(new ExternalBankException("connection timeout"));

        TransferRequestedEvent event = new TransferRequestedEvent(
                "SETTLE-merchant-2-20240101", MERCHANT_ID, BANK_ACCOUNT, AMOUNT, Instant.now());
        kafkaTemplate.send(TransferRequestedEvent.TOPIC, objectMapper.writeValueAsString(event));

        await().atMost(10, SECONDS)
               .pollInterval(Duration.ofMillis(300))
               .untilAsserted(() -> {
                   var transfer = transferRepository.findByExternalId("SETTLE-merchant-2-20240101");
                   assertThat(transfer).isPresent();
                   assertThat(transfer.get().getStatus()).isEqualTo(TransferStatus.FAILED);
               });
    }

    @Test
    @DisplayName("동일 이벤트 중복 수신 — 은행 재호출 없이 Transfer 1건 유지 (at-least-once 멱등성)")
    void duplicateEvent_isIdempotent_bankCalledOnce() throws Exception {
        given(externalBankClient.transfer(any()))
                .willReturn(new ExternalBankTransferResult.Succeeded("BANK-REF-IDEM", Map.of()));

        String payload = objectMapper.writeValueAsString(
                new TransferRequestedEvent(EXTERNAL_ID, MERCHANT_ID, BANK_ACCOUNT, AMOUNT, Instant.now()));

        kafkaTemplate.send(TransferRequestedEvent.TOPIC, payload);
        kafkaTemplate.send(TransferRequestedEvent.TOPIC, payload);

        await().atMost(10, SECONDS)
               .pollInterval(Duration.ofMillis(300))
               .untilAsserted(() ->
                       assertThat(transferRepository.findAll()).hasSize(1));
    }
}