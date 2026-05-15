package juyeop.jpay.payment.controller;

import juyeop.jpay.common.idempotency.IdempotencyStore;
import juyeop.jpay.payment.dto.ChargeRequest;
import juyeop.jpay.payment.dto.ChargeResponse;
import juyeop.jpay.payment.entity.ChargeStatus;
import juyeop.jpay.payment.pg.PgClient;
import juyeop.jpay.payment.pg.dto.PgAuthorizeResult;
import juyeop.jpay.payment.repository.ChargeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                "app.snowflake.node-id=99"
        }
)
@Testcontainers
class ChargeControllerIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("payment_db")
            .withUsername("jpay")
            .withPassword("jpay");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ChargeRepository chargeRepository;

    @MockitoBean
    PgClient pgClient;

    private static final Long USER_ID = 1L;
    private static final String PM_ID = "card-1234567890123456";
    private static final long AMOUNT = 10_000L;

    @BeforeEach
    void setUp() {
        chargeRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        given(pgClient.authorize(any()))
                .willReturn(new PgAuthorizeResult.Approved("PG-001", Map.of()));
    }

    // =========================================================================
    // Redis 캐시 저장
    // =========================================================================

    @Test
    void charge_firstRequest_storesResultInRedis() {
        String key = UUID.randomUUID().toString();

        post(key, new ChargeRequest(AMOUNT, PM_ID));

        assertThat(redisTemplate.hasKey("idempotency:" + key)).isTrue();
    }

    // =========================================================================
    // Redis 캐시 재사용
    // =========================================================================

    @Test
    void charge_sameKeyTwice_returnsCachedResponse_withoutCallingPgAgain() {
        String key = UUID.randomUUID().toString();
        ChargeRequest request = new ChargeRequest(AMOUNT, PM_ID);

        ResponseEntity<ChargeResponse> first = post(key, request);
        ResponseEntity<ChargeResponse> second = post(key, request);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().chargeId()).isEqualTo(first.getBody().chargeId());
        assertThat(second.getBody().status()).isEqualTo(ChargeStatus.COMPLETED);
        // PG는 첫 번째 요청에서만 호출되어야 한다
        verify(pgClient, times(1)).authorize(any());
    }

    // =========================================================================
    // Redis 장애 fallback
    // =========================================================================

    @Test
    void charge_redisStoreThrows_fallsBackToNormalExecution() {
        // IdempotencyStore가 예외를 던지는 상황을 시뮬레이션한다.
        // 실제 Redis를 멈추는 대신 IdempotencyStore 빈을 교체해서 테스트한다.
        // hint: @MockitoBean IdempotencyStore idempotencyStore + given(...).willThrow(...)
        // 단, 이 테스트를 위해 클래스 상단 @MockitoBean 선언이 필요하고
        // setUp()의 pgClient mock이 여전히 동작해야 한다.
        //
        // 검증: 예외 없이 200/201 응답을 받아야 한다.
    }

    // =========================================================================
    // helper
    // =========================================================================

    private ResponseEntity<ChargeResponse> post(String idempotencyKey, ChargeRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        headers.set("X-User-Id", String.valueOf(USER_ID));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/charges", HttpMethod.POST, new HttpEntity<>(body, headers), ChargeResponse.class);
    }
}