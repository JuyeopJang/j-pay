package juyeop.jpay.payment.controller;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.payment.dto.PaymentRequest;
import juyeop.jpay.payment.dto.PaymentResponse;
import juyeop.jpay.payment.entity.PaymentStatus;
import juyeop.jpay.payment.entity.UserBalance;
import juyeop.jpay.payment.repository.PaymentRepository;
import juyeop.jpay.payment.repository.UserBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
class PaymentControllerIntegrationTest {

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
    PaymentRepository paymentRepository;

    @Autowired
    UserBalanceRepository userBalanceRepository;

    private static final Long USER_ID = 1L;
    private static final String MERCHANT_ID = "merchant-abc";
    private static final long INITIAL_BALANCE = 50_000L;
    private static final long AMOUNT = 10_000L;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        userBalanceRepository.deleteAll();
        userBalanceRepository.save(UserBalance.create(USER_ID, Money.of(INITIAL_BALANCE)));
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    // =========================================================================
    // Redis 캐시 저장
    // =========================================================================

    @Test
    void pay_firstRequest_storesResultInRedis() {
        String key = UUID.randomUUID().toString();

        post(key, new PaymentRequest(AMOUNT, MERCHANT_ID));

        assertThat(redisTemplate.hasKey("idempotency:" + key)).isTrue();
    }

    // =========================================================================
    // Redis 캐시 재사용
    // =========================================================================

    @Test
    void pay_sameKeyTwice_returnsCachedResponse_balanceDeductedOnce() {
        String key = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest(AMOUNT, MERCHANT_ID);

        ResponseEntity<PaymentResponse> first = post(key, request);
        ResponseEntity<PaymentResponse> second = post(key, request);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().paymentId()).isEqualTo(first.getBody().paymentId());
        assertThat(second.getBody().status()).isEqualTo(PaymentStatus.COMPLETED);
        // 잔액은 한 번만 차감되어야 한다
        assertThat(userBalanceRepository.findByUserId(USER_ID))
                .isPresent()
                .hasValueSatisfying(b ->
                        assertThat(b.getBalance()).isEqualTo(Money.of(INITIAL_BALANCE - AMOUNT)));
    }

    // =========================================================================
    // Redis 장애 fallback
    // =========================================================================

    @Test
    void pay_redisStoreThrows_fallsBackToNormalExecution() {
        // hint: ChargeControllerIntegrationTest의 동일 테스트 참고
    }

    // =========================================================================
    // helper
    // =========================================================================

    private ResponseEntity<PaymentResponse> post(String idempotencyKey, PaymentRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        headers.set("X-User-Id", String.valueOf(USER_ID));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/payments/optimistic", HttpMethod.POST, new HttpEntity<>(body, headers), PaymentResponse.class);
    }
}