package juyeop.jpay.payment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.event.PaymentCompletedEvent;
import juyeop.jpay.payment.AbstractPaymentKafkaIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.snowflake.node-id=99",
                "app.scheduling.enabled=true"
        }
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboxPublisherKafkaTest extends AbstractPaymentKafkaIntegrationTest {

    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeAll
    void createShedLockTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS shedlock (
                    name       VARCHAR(64)  NOT NULL,
                    lock_until TIMESTAMP(3) NOT NULL,
                    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    locked_by  VARCHAR(255) NOT NULL,
                    PRIMARY KEY (name)
                )
                """);
    }

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
    }

    @Test
    @DisplayName("단건 이벤트 — 스케줄러가 Kafka로 발행하고 published=true로 갱신")
    void scheduler_picksUpUnpublishedEvent_andMarksPublished() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(9001L, 1L, "merchant-1", 10_000L, Instant.now());
        OutboxEvent saved = outboxEventRepository.save(
                OutboxEvent.create(9001L, PaymentCompletedEvent.TOPIC, event, objectMapper));

        await().atMost(5, SECONDS)
               .pollInterval(Duration.ofMillis(200))
               .untilAsserted(() ->
                       assertThat(outboxEventRepository.findById(saved.getId()))
                               .isPresent()
                               .hasValueSatisfying(e -> assertThat(e.isPublished()).isTrue()));
    }

    @Test
    @DisplayName("다건 이벤트 — 한 폴링 사이클에서 모두 발행되고 전부 published=true")
    void scheduler_publishesBatchOfEvents_allMarkedPublished() {
        List<OutboxEvent> events = LongStream.rangeClosed(9010L, 9014L)
                .mapToObj(id -> {
                    PaymentCompletedEvent e = new PaymentCompletedEvent(id, 1L, "merchant-batch", 1_000L, Instant.now());
                    return OutboxEvent.create(id, PaymentCompletedEvent.TOPIC, e, objectMapper);
                })
                .map(outboxEventRepository::save)
                .toList();

        await().atMost(5, SECONDS)
               .pollInterval(Duration.ofMillis(200))
               .untilAsserted(() -> {
                   List<OutboxEvent> persisted = outboxEventRepository.findAllById(
                           events.stream().map(OutboxEvent::getId).toList());
                   assertThat(persisted).hasSize(5)
                                        .allSatisfy(e -> assertThat(e.isPublished()).isTrue());
               });
    }
}