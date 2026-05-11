package juyeop.jpay.payment.service;

import juyeop.jpay.common.web.error.BusinessException;
import juyeop.jpay.payment.dto.ChargeRequest;
import juyeop.jpay.payment.dto.ChargeResponse;
import juyeop.jpay.payment.entity.ChargeStatus;
import juyeop.jpay.payment.exception.ChargeErrorType;
import juyeop.jpay.payment.pg.PgClient;
import juyeop.jpay.payment.pg.PgException;
import juyeop.jpay.payment.pg.dto.PgAuthorizeResult;
import juyeop.jpay.payment.repository.ChargeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                "app.snowflake.node-id=99"
        }
)
@Testcontainers
class ChargeFacadeServiceIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("payment_db")
            .withUsername("jpay")
            .withPassword("jpay");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    ChargeFacadeService chargeFacadeService;

    @Autowired
    ChargeRepository chargeRepository;

    @MockitoBean
    PgClient pgClient;

    private static final Long USER_ID = 1L;
    private static final String PM_ID = "card-1234567890123456";
    private static final long AMOUNT = 10_000L;

    @BeforeEach
    void cleanUp() {
        chargeRepository.deleteAll();
    }

    // --- 정상 흐름 ---

    @Test
    void charge_pgApproved_returnsCompleted() {
        given(pgClient.authorize(any()))
                .willReturn(new PgAuthorizeResult.Approved("PG-APPROVAL-001", Map.of()));

        ChargeResponse response = chargeFacadeService.charge(
                "idem-approved", USER_ID, new ChargeRequest(AMOUNT, PM_ID));

        assertThat(response.status()).isEqualTo(ChargeStatus.COMPLETED);
        assertThat(response.pgApprovalNumber()).isEqualTo("PG-APPROVAL-001");

        assertThat(chargeRepository.findByExternalId("idem-approved"))
                .isPresent()
                .hasValueSatisfying(c -> {
                    assertThat(c.getStatus()).isEqualTo(ChargeStatus.COMPLETED);
                    assertThat(c.getPgApprovalNumber()).isEqualTo("PG-APPROVAL-001");
                });
    }

    @Test
    void charge_pgDeclined_returnsFailed() {
        given(pgClient.authorize(any()))
                .willReturn(new PgAuthorizeResult.Declined("LIMIT_EXCEEDED", "카드 한도 초과", Map.of()));

        ChargeResponse response = chargeFacadeService.charge(
                "idem-declined", USER_ID, new ChargeRequest(AMOUNT, PM_ID));

        assertThat(response.status()).isEqualTo(ChargeStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("카드 한도 초과");

        assertThat(chargeRepository.findByExternalId("idem-declined"))
                .isPresent()
                .hasValueSatisfying(c -> {
                    assertThat(c.getStatus()).isEqualTo(ChargeStatus.FAILED);
                    assertThat(c.getFailureReason()).isEqualTo("카드 한도 초과");
                });
    }

    @Test
    void charge_pgException_throwsUpstreamUnavailable_andChargeIsFailed() {
        given(pgClient.authorize(any()))
                .willThrow(new PgException("connection refused"));

        assertThatThrownBy(() -> chargeFacadeService.charge(
                "idem-pg-err", USER_ID, new ChargeRequest(AMOUNT, PM_ID)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorType())
                        .isEqualTo(ChargeErrorType.UPSTREAM_UNAVAILABLE));

        assertThat(chargeRepository.findByExternalId("idem-pg-err"))
                .isPresent()
                .hasValueSatisfying(c -> {
                    assertThat(c.getStatus()).isEqualTo(ChargeStatus.FAILED);
                    assertThat(c.getFailureReason()).isEqualTo("UPSTREAM_FAILURE");
                });

        verify(pgClient, times(1)).authorize(any());
    }

    // --- 멱등성 ---

    @Test
    void charge_sameKeyTwice_replaysPreviousResult_withoutCallingPgAgain() {
        given(pgClient.authorize(any()))
                .willReturn(new PgAuthorizeResult.Approved("PG-APPROVAL-002", Map.of()));

        ChargeRequest request = new ChargeRequest(AMOUNT, PM_ID);
        ChargeResponse first  = chargeFacadeService.charge("idem-replay", USER_ID, request);
        ChargeResponse second = chargeFacadeService.charge("idem-replay", USER_ID, request);

        assertThat(second.chargeId()).isEqualTo(first.chargeId());
        assertThat(second.status()).isEqualTo(first.status());
        assertThat(second.pgApprovalNumber()).isEqualTo(first.pgApprovalNumber());
        verify(pgClient, times(1)).authorize(any());
    }

    @Test
    void charge_sameKeyDifferentUserId_throwsIdempotencyConflict() {
        given(pgClient.authorize(any()))
                .willReturn(new PgAuthorizeResult.Approved("PG-APPROVAL-003", Map.of()));

        chargeFacadeService.charge("idem-conflict", USER_ID, new ChargeRequest(AMOUNT, PM_ID));

        assertThatThrownBy(() ->
                chargeFacadeService.charge("idem-conflict", 99L, new ChargeRequest(AMOUNT, PM_ID)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorType())
                        .isEqualTo(ChargeErrorType.IDEMPOTENCY_CONFLICT));
    }

    // --- 동시성 ---

    @Test
    void charge_concurrentDuplicateInsert_bothThreadsGetValidResult() throws InterruptedException {
        given(pgClient.authorize(any()))
                .willReturn(new PgAuthorizeResult.Approved("PG-APPROVAL-CONCURRENT", Map.of()));

        ChargeRequest request = new ChargeRequest(AMOUNT, PM_ID);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<ChargeResponse> result1 = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<ChargeResponse> result2 = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> error1 = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> error2 = new java.util.concurrent.atomic.AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try {
                start.await();
                result1.set(chargeFacadeService.charge("idem-concurrent", USER_ID, request));
            } catch (Throwable e) {
                error1.set(e);
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                start.await();
                result2.set(chargeFacadeService.charge("idem-concurrent", USER_ID, request));
            } catch (Throwable e) {
                error2.set(e);
            }
        });

        t1.start();
        t2.start();
        start.countDown();
        t1.join(10_000);
        t2.join(10_000);
        assertThat(t1.isAlive()).as("t1 did not finish in time").isFalse();
        assertThat(t2.isAlive()).as("t2 did not finish in time").isFalse();

        assertThat(error1.get()).isNull();
        assertThat(error2.get()).isNull();
        assertThat(result1.get().status()).isIn(ChargeStatus.COMPLETED, ChargeStatus.FAILED);
        assertThat(result2.get().status()).isIn(ChargeStatus.COMPLETED, ChargeStatus.FAILED);
        assertThat(result1.get()).isNotNull();
        assertThat(result2.get()).isNotNull();
        assertThat(result1.get().chargeId()).isEqualTo(result2.get().chargeId());
    }
}