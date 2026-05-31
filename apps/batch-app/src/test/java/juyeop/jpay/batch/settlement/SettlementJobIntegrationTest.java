package juyeop.jpay.batch.settlement;

import juyeop.jpay.batch.AbstractBatchIntegrationTest;
import juyeop.jpay.batch.entity.Settlement;
import juyeop.jpay.batch.entity.SettlementStatus;
import juyeop.jpay.batch.repository.SettlementOutboxEventRepository;
import juyeop.jpay.batch.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

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
class SettlementJobIntegrationTest extends AbstractBatchIntegrationTest {

    @Autowired
    JobLauncher jobLauncher;

    @Autowired
    @Qualifier("settlementJob")
    Job settlementJob;

    @Autowired
    SettlementRepository settlementRepository;

    @Autowired
    SettlementOutboxEventRepository outboxEventRepository;

    @Autowired
    @Qualifier("paymentJdbcTemplate")
    JdbcTemplate paymentJdbcTemplate;

    @MockitoBean
    KafkaTemplate<String, String> kafkaTemplate;

    private static final LocalDate YESTERDAY = LocalDate.now().minusDays(1);
    private static final String YESTERDAY_STR = YESTERDAY.toString();

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        settlementRepository.deleteAllInBatch();
        paymentJdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS payments (
                    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                    merchant_id VARCHAR(64)  NOT NULL,
                    amount      BIGINT       NOT NULL,
                    status      VARCHAR(20)  NOT NULL,
                    completed_at DATETIME
                )
                """);
        paymentJdbcTemplate.execute("DELETE FROM payments");
    }

    @Test
    @DisplayName("정산 Job → 가맹점별 Settlement + OutboxEvent 생성")
    void settlementJob_aggregatesPaymentsAndCreatesSettlementWithOutbox() throws Exception {
        paymentJdbcTemplate.update(
                "INSERT INTO payments (merchant_id, amount, status, completed_at) VALUES (?, ?, 'COMPLETED', ?)",
                "merchant-A", 50000L, YESTERDAY_STR + " 10:00:00");
        paymentJdbcTemplate.update(
                "INSERT INTO payments (merchant_id, amount, status, completed_at) VALUES (?, ?, 'COMPLETED', ?)",
                "merchant-A", 30000L, YESTERDAY_STR + " 11:00:00");
        paymentJdbcTemplate.update(
                "INSERT INTO payments (merchant_id, amount, status, completed_at) VALUES (?, ?, 'COMPLETED', ?)",
                "merchant-B", 20000L, YESTERDAY_STR + " 12:00:00");

        JobExecution execution = jobLauncher.run(settlementJob, jobParams());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<Settlement> settlements = settlementRepository.findAll();
        assertThat(settlements).hasSize(2);
        assertThat(settlements).extracting(Settlement::getMerchantId)
                .containsExactlyInAnyOrder("merchant-A", "merchant-B");

        Settlement merchantA = settlements.stream()
                .filter(s -> "merchant-A".equals(s.getMerchantId())).findFirst().orElseThrow();
        assertThat(merchantA.getTotalAmount()).isEqualTo(80_000L);
        assertThat(merchantA.getPaymentCount()).isEqualTo(2);
        assertThat(merchantA.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(merchantA.getTransferExternalId()).isEqualTo("SETTLE-merchant-A-" + YESTERDAY_STR);

        assertThat(outboxEventRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("이미 정산된 가맹점은 Skip → 중복 Settlement 생성 없음")
    void settlementJob_skipsAlreadySettledMerchant() throws Exception {
        settlementRepository.save(Settlement.of(
                "merchant-A", YESTERDAY, YESTERDAY, 80_000L, 2,
                "SETTLE-merchant-A-" + YESTERDAY_STR));

        paymentJdbcTemplate.update(
                "INSERT INTO payments (merchant_id, amount, status, completed_at) VALUES (?, ?, 'COMPLETED', ?)",
                "merchant-A", 80000L, YESTERDAY_STR + " 10:00:00");
        paymentJdbcTemplate.update(
                "INSERT INTO payments (merchant_id, amount, status, completed_at) VALUES (?, ?, 'COMPLETED', ?)",
                "merchant-B", 15000L, YESTERDAY_STR + " 11:00:00");

        JobExecution execution = jobLauncher.run(settlementJob, jobParams());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        assertThat(settlementRepository.findAll()).hasSize(2);
        assertThat(outboxEventRepository.findAll()).hasSize(1);
        assertThat(settlementRepository.findByTransferExternalId("SETTLE-merchant-B-" + YESTERDAY_STR)).isPresent();
    }

    private JobParameters jobParams() {
        return new JobParametersBuilder()
                .addString("periodStart", YESTERDAY_STR)
                .addString("periodEnd", YESTERDAY_STR)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
    }
}
