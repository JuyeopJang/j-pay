package juyeop.jpay.batch.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.batch.AbstractBatchIntegrationTest;
import juyeop.jpay.batch.entity.Settlement;
import juyeop.jpay.batch.entity.SettlementStatus;
import juyeop.jpay.batch.repository.SettlementRepository;
import juyeop.jpay.common.event.TransferCompletedEvent;
import juyeop.jpay.common.event.TransferFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.batch.job.enabled=false",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
        }
)
class TransferResultConsumerTest extends AbstractBatchIntegrationTest {

    @Autowired
    TransferResultConsumer transferResultConsumer;

    @Autowired
    SettlementRepository settlementRepository;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    KafkaTemplate<String, String> kafkaTemplate;

    private static final String EXTERNAL_ID = "SETTLE-merchant-A-2024-01-01";
    private static final LocalDate PERIOD    = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        settlementRepository.deleteAllInBatch();
        settlementRepository.save(
                Settlement.of("merchant-A", PERIOD, PERIOD, 80_000L, 2, EXTERNAL_ID));
    }

    @Test
    @DisplayName("TransferCompleted 이벤트 수신 → Settlement TRANSFERRED")
    void consume_transferCompleted_marksSettlementTransferred() throws Exception {
        TransferCompletedEvent event = new TransferCompletedEvent(
                EXTERNAL_ID, "merchant-A", 80_000L, "BANK-REF-001", Instant.now());

        transferResultConsumer.consume(
                objectMapper.writeValueAsString(event),
                TransferCompletedEvent.TOPIC);

        Settlement updated = settlementRepository.findByTransferExternalId(EXTERNAL_ID).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SettlementStatus.TRANSFERRED);
    }

    @Test
    @DisplayName("TransferFailed 이벤트 수신 → Settlement FAILED")
    void consume_transferFailed_marksSettlementFailed() throws Exception {
        TransferFailedEvent event = new TransferFailedEvent(
                EXTERNAL_ID, "merchant-A", 80_000L, "BANK_REJECT", Instant.now());

        transferResultConsumer.consume(
                objectMapper.writeValueAsString(event),
                TransferFailedEvent.TOPIC);

        Settlement updated = settlementRepository.findByTransferExternalId(EXTERNAL_ID).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SettlementStatus.FAILED);
    }

    @Test
    @DisplayName("이미 TRANSFERRED인 Settlement — 재수신해도 상태 변경 없음")
    void consume_alreadyTransferred_isIgnored() throws Exception {
        // 먼저 TRANSFERRED로 변경
        transferResultConsumer.consume(
                objectMapper.writeValueAsString(
                        new TransferCompletedEvent(EXTERNAL_ID, "merchant-A", 80_000L, "REF-001", Instant.now())),
                TransferCompletedEvent.TOPIC);

        // 이후 FAILED 이벤트 재수신
        transferResultConsumer.consume(
                objectMapper.writeValueAsString(
                        new TransferFailedEvent(EXTERNAL_ID, "merchant-A", 80_000L, "LATE_FAIL", Instant.now())),
                TransferFailedEvent.TOPIC);

        Settlement settlement = settlementRepository.findByTransferExternalId(EXTERNAL_ID).orElseThrow();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.TRANSFERRED);
    }
}