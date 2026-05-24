package juyeop.jpay.common.jpa;

import juyeop.jpay.common.core.SnowflakeIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaInfraConfig {

    public JpaInfraConfig(@Value("${app.snowflake.node-id}") long nodeId) {
        SnowflakeIds.init(nodeId);
    }
}