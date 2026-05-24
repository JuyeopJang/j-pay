package juyeop.jpay.batch.settlement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.batch.entity.Settlement;
import juyeop.jpay.batch.entity.SettlementOutboxEvent;
import juyeop.jpay.batch.repository.SettlementOutboxEventRepository;
import juyeop.jpay.batch.repository.SettlementRepository;
import juyeop.jpay.batch.settlement.dto.SettlementSummary;
import juyeop.jpay.common.event.TransferRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Outbox 패턴 적용: Writer에서 Kafka를 직접 호출하면 저장 성공 후 크래시 시 이벤트 유실.
 * Settlement 저장과 outbox INSERT를 같은 청크 트랜잭션으로 묶어 원자성을 보장한다.
 */
@Slf4j
@Configuration
public class SettlementJobConfig {

    private final SettlementRepository settlementRepository;
    private final SettlementOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final DataSource paymentDataSource;

    @Value("${app.settlement.default-bank-account-id}")
    private String defaultBankAccountId;

    public SettlementJobConfig(SettlementRepository settlementRepository,
                               SettlementOutboxEventRepository outboxEventRepository,
                               ObjectMapper objectMapper,
                               @Qualifier("paymentDataSource") DataSource paymentDataSource) {
        this.settlementRepository = settlementRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.paymentDataSource = paymentDataSource;
    }

    @Bean
    public Job settlementJob(JobRepository jobRepository, Step settlementStep) {
        return new JobBuilder("settlementJob", jobRepository)
                .start(settlementStep)
                .build();
    }

    @Bean
    public Step settlementStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               JdbcCursorItemReader<SettlementSummary> settlementReader) {
        return new StepBuilder("settlementStep", jobRepository)
                .<SettlementSummary, SettlementSummary>chunk(100, transactionManager)
                .reader(settlementReader)
                .writer(settlementWriter())
                .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<SettlementSummary> settlementReader(
            @Value("#{jobParameters['periodStart']}") String periodStart,
            @Value("#{jobParameters['periodEnd']}") String periodEnd) {

        return new JdbcCursorItemReaderBuilder<SettlementSummary>()
                .name("settlementReader")
                .dataSource(paymentDataSource)
                .sql("""
                        SELECT merchant_id,
                               SUM(amount)  AS total_amount,
                               COUNT(*)     AS payment_count
                        FROM payments
                        WHERE status = 'COMPLETED'
                          AND DATE(completed_at) BETWEEN ? AND ?
                        GROUP BY merchant_id
                        """)
                .preparedStatementSetter(ps -> {
                    ps.setString(1, periodStart);
                    ps.setString(2, periodEnd);
                })
                .rowMapper((rs, i) -> new SettlementSummary(
                        rs.getString("merchant_id"),
                        rs.getLong("total_amount"),
                        rs.getInt("payment_count")))
                .build();
    }

    @Bean
    public ItemWriter<SettlementSummary> settlementWriter() {
        return chunk -> {
            for (SettlementSummary summary : chunk.getItems()) {
                String externalId = "SETTLE-" + summary.merchantId() + "-" + LocalDate.now().minusDays(1);

                if (settlementRepository.findByTransferExternalId(externalId).isPresent()) {
                    log.info("Settlement already exists, skip: {}", externalId);
                    continue;
                }

                Settlement settlement = Settlement.of(
                        summary.merchantId(),
                        LocalDate.now().minusDays(1),
                        LocalDate.now().minusDays(1),
                        summary.totalAmount(),
                        summary.paymentCount(),
                        externalId);
                settlementRepository.save(settlement);

                TransferRequestedEvent event = new TransferRequestedEvent(
                        externalId,
                        summary.merchantId(),
                        defaultBankAccountId,
                        summary.totalAmount(),
                        Instant.now());
                SettlementOutboxEvent outboxEvent = SettlementOutboxEvent.create(
                        TransferRequestedEvent.TOPIC,
                        externalId,
                        serialize(event));
                outboxEventRepository.save(outboxEvent);

                log.info("settlement outbox enqueued: externalId={}, amount={}", externalId, summary.totalAmount());
            }
        };
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
