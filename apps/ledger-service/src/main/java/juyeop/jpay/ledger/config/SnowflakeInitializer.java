package juyeop.jpay.ledger.config;

import juyeop.jpay.common.core.SnowflakeIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 애플리케이션 부팅 시 SnowflakeIds 정적 싱글턴 초기화.
 * @Configuration 빈 생성자 단계에서 init → EntityManagerFactory 생성보다 먼저 실행됨.
 */
@Configuration
public class SnowflakeInitializer {

    public SnowflakeInitializer(@Value("${app.snowflake.node-id}") long nodeId) {
        SnowflakeIds.init(nodeId);
    }
}