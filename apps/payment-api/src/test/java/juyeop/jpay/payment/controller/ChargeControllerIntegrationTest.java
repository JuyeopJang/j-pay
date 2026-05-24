package juyeop.jpay.payment.controller;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.payment.AbstractPaymentIntegrationTest;
import juyeop.jpay.payment.bank.BankTransferClient;
import juyeop.jpay.payment.bank.dto.BankTransferResult;
import juyeop.jpay.payment.dto.ChargeRequest;
import juyeop.jpay.payment.dto.ChargeResponse;
import juyeop.jpay.payment.entity.ChargeStatus;
import juyeop.jpay.payment.entity.UserBalance;
import juyeop.jpay.payment.repository.ChargeRepository;
import juyeop.jpay.payment.repository.UserBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
class ChargeControllerIntegrationTest extends AbstractPaymentIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ChargeRepository chargeRepository;

    @MockitoBean
    BankTransferClient bankTransferClient;

    @MockitoBean
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    UserBalanceRepository userBalanceRepository;

    private static final Long USER_ID        = 1L;
    private static final String BANK_ACCOUNT_ID = "bank-acc-1234567890123456";
    private static final long AMOUNT         = 10_000L;

    @BeforeEach
    void setUp() {
        chargeRepository.deleteAll();
        userBalanceRepository.deleteAll();
        userBalanceRepository.save(UserBalance.create(USER_ID, Money.of(1_000_000L)));
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        given(bankTransferClient.transfer(any()))
                .willReturn(new BankTransferResult.Succeeded("TRANSFER-001", Map.of()));
    }

    // =========================================================================
    // Redis 캐시 저장
    // =========================================================================

    @Test
    void charge_firstRequest_storesResultInRedis() {
        String key = UUID.randomUUID().toString();

        post(key, new ChargeRequest(AMOUNT, BANK_ACCOUNT_ID));

        assertThat(redisTemplate.hasKey("idempotency:" + key)).isTrue();
    }

    // =========================================================================
    // Redis 캐시 재사용
    // =========================================================================

    @Test
    void charge_sameKeyTwice_returnsCachedResponse_withoutCallingBankAgain() {
        String key = UUID.randomUUID().toString();
        ChargeRequest request = new ChargeRequest(AMOUNT, BANK_ACCOUNT_ID);

        ResponseEntity<ChargeResponse> first = post(key, request);
        ResponseEntity<ChargeResponse> second = post(key, request);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().chargeId()).isEqualTo(first.getBody().chargeId());
        assertThat(second.getBody().status()).isEqualTo(ChargeStatus.COMPLETED);
        verify(bankTransferClient, times(1)).transfer(any());
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
        // setUp()의 bankTransferClient mock이 여전히 동작해야 한다.
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