package juyeop.jpay.batch.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job settlementJob;
    private final Job reconciliationJob;

    public BatchScheduler(JobLauncher jobLauncher,
                          @Qualifier("settlementJob") Job settlementJob,
                          @Qualifier("reconciliationJob") Job reconciliationJob) {
        this.jobLauncher = jobLauncher;
        this.settlementJob = settlementJob;
        this.reconciliationJob = reconciliationJob;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runSettlement() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        JobParameters params = new JobParametersBuilder()
                .addString("periodStart", yesterday.toString())
                .addString("periodEnd", yesterday.toString())
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();
        executeJob(settlementJob, params);
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void runReconciliation() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        JobParameters params = new JobParametersBuilder()
                .addString("reconciliationDate", yesterday.toString())
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();
        executeJob(reconciliationJob, params);
    }

    private void executeJob(Job job, JobParameters params) {
        try {
            jobLauncher.run(job, params);
        } catch (Exception e) {
            log.error("{} failed: {}", job.getName(), e.getMessage(), e);
        }
    }
}
