package juyeop.jpay.batch.reconciliation;

import juyeop.jpay.batch.entity.Discrepancy;
import juyeop.jpay.batch.reconciliation.dto.BankTransaction;
import juyeop.jpay.batch.repository.DiscrepancyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 두 Step으로 구성한 이유: 양방향 비교를 단일 Step에서 처리하려면 전체 데이터를 메모리에 올려야 한다.
 * Step 2를 Tasklet으로 구현한 이유: 집합 차집합 연산은 Set을 한 번에 로드해 비교하는 것이 chunk보다 단순하다.
 */
@Slf4j
@Configuration
public class ReconciliationJobConfig {

    private final DiscrepancyRepository discrepancyRepository;
    private final BankMockClient bankMockClient;
    private final JdbcTemplate paymentJdbcTemplate;

    public ReconciliationJobConfig(DiscrepancyRepository discrepancyRepository,
                                   BankMockClient bankMockClient,
                                   @Qualifier("paymentJdbcTemplate") JdbcTemplate paymentJdbcTemplate) {
        this.discrepancyRepository = discrepancyRepository;
        this.bankMockClient = bankMockClient;
        this.paymentJdbcTemplate = paymentJdbcTemplate;
    }

    @Bean
    public Job reconciliationJob(JobRepository jobRepository,
                                 Step reconciliationStep,
                                 Step missingInBankStep) {
        return new JobBuilder("reconciliationJob", jobRepository)
                .start(reconciliationStep)
                .next(missingInBankStep)
                .build();
    }

    // ─── Step 1: 은행 목록 → 내부 비교 (MISSING_IN_INTERNAL, AMOUNT_MISMATCH) ────────────

    @Bean
    public Step reconciliationStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   ListItemReader<BankTransaction> reconciliationReader,
                                   ItemProcessor<BankTransaction, Discrepancy> reconciliationProcessor) {
        return new StepBuilder("reconciliationStep", jobRepository)
                .<BankTransaction, Discrepancy>chunk(50, transactionManager)
                .reader(reconciliationReader)
                .processor(reconciliationProcessor)
                .writer(reconciliationWriter())
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(10)
                .build();
    }

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public ListItemReader<BankTransaction> reconciliationReader(
            @Value("#{jobParameters['reconciliationDate']}") String date) {
        List<BankTransaction> transactions = bankMockClient.fetchTransactions(LocalDate.parse(date));
        log.info("Bank transactions fetched: date={}, count={}", date, transactions.size());
        return new ListItemReader<>(transactions);
    }

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public ItemProcessor<BankTransaction, Discrepancy> reconciliationProcessor(
            @Value("#{jobParameters['reconciliationDate']}") String date) {
        LocalDate reconciliationDate = LocalDate.parse(date);
        return bankTx -> {
            List<InternalCharge> rows = paymentJdbcTemplate.query(
                    "SELECT external_id, amount FROM charges WHERE transfer_ref = ? AND status = 'COMPLETED'",
                    (rs, i) -> new InternalCharge(rs.getString("external_id"), rs.getLong("amount")),
                    bankTx.transferRef());

            if (rows.isEmpty()) {
                return Discrepancy.missingInInternal(reconciliationDate, bankTx.transferRef(), bankTx.amount());
            }
            InternalCharge internal = rows.get(0);
            if (internal.amount() != bankTx.amount()) {
                return Discrepancy.amountMismatch(reconciliationDate, internal.externalId(),
                        bankTx.transferRef(), internal.amount(), bankTx.amount());
            }
            return null; // 정상 건 — Writer 스킵
        };
    }

    @Bean
    public ItemWriter<Discrepancy> reconciliationWriter() {
        return chunk -> {
            List<Discrepancy> items = (List<Discrepancy>) chunk.getItems();
            discrepancyRepository.saveAll(items);
            log.info("Discrepancies saved: count={}", items.size());
        };
    }

    // ─── Step 2: 내부 목록 → 은행 비교 (MISSING_IN_BANK) — Tasklet ──────────────────────

    @Bean
    public Step missingInBankStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  Tasklet missingInBankTasklet) {
        return new StepBuilder("missingInBankStep", jobRepository)
                .tasklet(missingInBankTasklet, transactionManager)
                .build();
    }

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public Tasklet missingInBankTasklet(
            @Value("#{jobParameters['reconciliationDate']}") String date) {
        return (contribution, chunkContext) -> {
            LocalDate reconciliationDate = LocalDate.parse(date);

            Set<String> bankRefs = bankMockClient.fetchTransactions(reconciliationDate).stream()
                    .map(BankTransaction::transferRef)
                    .collect(Collectors.toSet());

            List<Discrepancy> discrepancies = paymentJdbcTemplate.query(
                    "SELECT external_id, transfer_ref, amount FROM charges " +
                    "WHERE status = 'COMPLETED' AND DATE(completed_at) = ?",
                    (rs, i) -> {
                        String transferRef = rs.getString("transfer_ref");
                        if (bankRefs.contains(transferRef)) return null;
                        return Discrepancy.missingInBank(
                                reconciliationDate,
                                rs.getString("external_id"),
                                transferRef,
                                rs.getLong("amount"));
                    },
                    date).stream().filter(d -> d != null).toList();

            discrepancyRepository.saveAll(discrepancies);
            log.info("MISSING_IN_BANK discrepancies saved: date={}, count={}", date, discrepancies.size());
            return RepeatStatus.FINISHED;
        };
    }

    private record InternalCharge(String externalId, long amount) {}
}
