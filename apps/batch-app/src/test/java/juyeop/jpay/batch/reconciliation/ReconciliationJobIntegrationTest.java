package juyeop.jpay.batch.reconciliation;

import juyeop.jpay.batch.AbstractBatchIntegrationTest;
import juyeop.jpay.batch.entity.Discrepancy;
import juyeop.jpay.batch.entity.DiscrepancyType;
import juyeop.jpay.batch.reconciliation.dto.BankTransaction;
import juyeop.jpay.batch.repository.DiscrepancyRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.batch.job.enabled=false",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
        }
)
class ReconciliationJobIntegrationTest extends AbstractBatchIntegrationTest {

    @Autowired
    JobLauncher jobLauncher;

    @Autowired
    @Qualifier("reconciliationJob")
    Job reconciliationJob;

    @Autowired
    DiscrepancyRepository discrepancyRepository;

    @Autowired
    @Qualifier("paymentJdbcTemplate")
    JdbcTemplate paymentJdbcTemplate;

    @MockitoBean
    BankMockClient bankMockClient;

    @MockitoBean
    KafkaTemplate<String, String> kafkaTemplate;

    private static final LocalDate RECON_DATE = LocalDate.of(2024, 1, 1);
    private static final String RECON_DATE_STR = RECON_DATE.toString();

    @BeforeEach
    void setUp() {
        discrepancyRepository.deleteAllInBatch();
        paymentJdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS charges (
                    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                    external_id  VARCHAR(128) NOT NULL,
                    transfer_ref VARCHAR(64),
                    amount       BIGINT NOT NULL,
                    status       VARCHAR(20) NOT NULL,
                    completed_at DATETIME
                )
                """);
        paymentJdbcTemplate.execute("DELETE FROM charges");
    }

    @Test
    @DisplayName("대사 Job → MISSING_IN_INTERNAL / AMOUNT_MISMATCH / MISSING_IN_BANK 모두 감지")
    void reconciliationJob_detectsAllDiscrepancyTypes() throws Exception {
        // 은행: REF-MATCH(정상), REF-MISSING(내부없음), REF-MISMATCH(금액불일치)
        given(bankMockClient.fetchTransactions(any(LocalDate.class))).willReturn(List.of(
                new BankTransaction("REF-MATCH",   "ext-001", 10_000L, Instant.now()),
                new BankTransaction("REF-MISSING", "ext-002", 20_000L, Instant.now()),
                new BankTransaction("REF-MISMATCH","ext-003", 30_000L, Instant.now())
        ));

        // 내부: REF-MATCH(정상), REF-MISMATCH(금액불일치), REF-NOBANK(은행없음)
        insertCharge("ext-001", "REF-MATCH",   10_000L);
        insertCharge("ext-003", "REF-MISMATCH", 25_000L); // 금액 불일치 (30000 vs 25000)
        insertCharge("ext-004", "REF-NOBANK",   5_000L);  // 은행에 없음

        JobExecution execution = jobLauncher.run(reconciliationJob, jobParams());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<Discrepancy> discrepancies = discrepancyRepository.findAll();
        assertThat(discrepancies).hasSize(3);

        assertThat(discrepancies)
                .extracting(Discrepancy::getDiscrepancyType)
                .containsExactlyInAnyOrder(
                        DiscrepancyType.MISSING_IN_INTERNAL,
                        DiscrepancyType.AMOUNT_MISMATCH,
                        DiscrepancyType.MISSING_IN_BANK);

        Discrepancy missingInInternal = discrepancies.stream()
                .filter(d -> d.getDiscrepancyType() == DiscrepancyType.MISSING_IN_INTERNAL)
                .findFirst().orElseThrow();
        assertThat(missingInInternal.getTransferRef()).isEqualTo("REF-MISSING");
        assertThat(missingInInternal.getBankAmount()).isEqualTo(20_000L);

        Discrepancy amountMismatch = discrepancies.stream()
                .filter(d -> d.getDiscrepancyType() == DiscrepancyType.AMOUNT_MISMATCH)
                .findFirst().orElseThrow();
        assertThat(amountMismatch.getInternalAmount()).isEqualTo(25_000L);
        assertThat(amountMismatch.getBankAmount()).isEqualTo(30_000L);

        Discrepancy missingInBank = discrepancies.stream()
                .filter(d -> d.getDiscrepancyType() == DiscrepancyType.MISSING_IN_BANK)
                .findFirst().orElseThrow();
        assertThat(missingInBank.getTransferRef()).isEqualTo("REF-NOBANK");
        assertThat(missingInBank.getInternalAmount()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("은행과 내부 데이터가 완전히 일치하면 Discrepancy 생성 없음")
    void reconciliationJob_allMatch_noDiscrepancy() throws Exception {
        given(bankMockClient.fetchTransactions(any(LocalDate.class))).willReturn(List.of(
                new BankTransaction("REF-001", "ext-001", 10_000L, Instant.now()),
                new BankTransaction("REF-002", "ext-002", 20_000L, Instant.now())
        ));

        insertCharge("ext-001", "REF-001", 10_000L);
        insertCharge("ext-002", "REF-002", 20_000L);

        JobExecution execution = jobLauncher.run(reconciliationJob, jobParams());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(discrepancyRepository.findAll()).isEmpty();
    }

    private void insertCharge(String externalId, String transferRef, long amount) {
        paymentJdbcTemplate.update(
                "INSERT INTO charges (external_id, transfer_ref, amount, status, completed_at) VALUES (?, ?, ?, 'COMPLETED', ?)",
                externalId, transferRef, amount, RECON_DATE_STR + " 10:00:00");
    }

    private JobParameters jobParams() {
        return new JobParametersBuilder()
                .addString("reconciliationDate", RECON_DATE_STR)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
    }
}
