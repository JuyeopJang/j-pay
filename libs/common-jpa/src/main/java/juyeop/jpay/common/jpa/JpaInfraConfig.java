package juyeop.jpay.common.jpa;

import juyeop.jpay.common.core.SnowflakeIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 인프라 통합 설정 — Auditing 활성화 + Snowflake ID 정적 싱글턴 초기화.
 *
 * 사용 측 app은:
 *  1) build.gradle에 :libs:common-jpa 의존 추가
 *  2) 메인 클래스에 scanBasePackages = "juyeop.jpay" 또는 동등한 component scan 설정
 *  3) application.yml에 `app.snowflake.node-id` 값 지정
 */
@Configuration
@EnableJpaAuditing
public class JpaInfraConfig {

    public JpaInfraConfig(@Value("${app.snowflake.node-id}") long nodeId) {
        SnowflakeIds.init(nodeId);
    }
}