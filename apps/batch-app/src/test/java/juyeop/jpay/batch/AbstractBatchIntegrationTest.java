package juyeop.jpay.batch;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.lifecycle.Startables;

public abstract class AbstractBatchIntegrationTest {

    static final MySQLContainer<?> batchMysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("batch_db")
            .withUsername("jpay")
            .withPassword("jpay");

    // SettlementJob reader, ReconciliationJob processor/tasklet가 payment_db를 직접 조회
    static final MySQLContainer<?> paymentMysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("payment_db")
            .withUsername("jpay")
            .withPassword("jpay");

    static {
        Startables.deepStart(batchMysql, paymentMysql).join();
    }

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.jdbc-url", batchMysql::getJdbcUrl);
        registry.add("spring.datasource.username", batchMysql::getUsername);
        registry.add("spring.datasource.password", batchMysql::getPassword);
        registry.add("payment-db.datasource.jdbc-url", paymentMysql::getJdbcUrl);
        registry.add("payment-db.datasource.username", paymentMysql::getUsername);
        registry.add("payment-db.datasource.password", paymentMysql::getPassword);
    }
}