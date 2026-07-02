package juyeop.jpay.batch.config;

import jakarta.annotation.PostConstruct;
import juyeop.jpay.common.core.SnowflakeIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BatchInfraConfig {

    @Value("${app.snowflake.node-id}")
    private long nodeId;

    @PostConstruct
    public void initSnowflake() {
        SnowflakeIds.init(nodeId);
    }
}