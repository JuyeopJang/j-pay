package juyeop.jpay.batch.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

// batch-app은 DataSource가 두 개다 (batch_db + payment_db).
// DataSource 빈이 하나라도 수동 등록되면 Spring Boot의 DataSourceAutoConfiguration이 비활성화된다.
// 따라서 @Primary batchDataSource를 명시 등록해야 JPA와 Spring Batch가 batch_db를 바라본다.
// 두 DataSource 모두 @ConfigurationProperties로 HikariCP에 직접 바인딩 → url이 아닌 jdbc-url 사용.
@Configuration
public class PaymentDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource batchDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @ConfigurationProperties(prefix = "payment-db.datasource")
    public DataSource paymentDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public JdbcTemplate paymentJdbcTemplate(@Qualifier("paymentDataSource") DataSource paymentDataSource) {
        return new JdbcTemplate(paymentDataSource);
    }

    @Bean
    public PlatformTransactionManager paymentTransactionManager(
            @Qualifier("paymentDataSource") DataSource paymentDataSource) {
        return new DataSourceTransactionManager(paymentDataSource);
    }
}
