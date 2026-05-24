package juyeop.jpay.payment.service;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.web.error.BusinessException;
import juyeop.jpay.payment.AbstractPaymentIntegrationTest;
import juyeop.jpay.payment.bank.BankTransferClient;
import juyeop.jpay.payment.bank.BankTransferException;
import juyeop.jpay.payment.bank.dto.BankTransferResult;
import juyeop.jpay.payment.dto.ChargeRequest;
import juyeop.jpay.payment.dto.ChargeResponse;
import juyeop.jpay.payment.entity.ChargeStatus;
import juyeop.jpay.payment.entity.UserBalance;
import juyeop.jpay.payment.exception.ChargeErrorType;
import juyeop.jpay.payment.repository.ChargeRepository;
import juyeop.jpay.payment.repository.UserBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
                        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                "app.snowflake.node-id=99"
        }
)
class ChargeFacadeServiceIntegrationTest extends AbstractPaymentIntegrationTest {

    @Autowired
    ChargeFacadeService chargeFacadeService;

    @Autowired
    ChargeRepository chargeRepository;

    @Autowired
    UserBalanceRepository userBalanceRepository;

    @MockitoBean
    BankTransferClient bankTransferClient;

    @MockitoBean
    KafkaTemplate<String, String> kafkaTemplate;

    private static final Long USER_ID = 1L;
    private static final String BANK_ACCOUNT_ID = "bank-acc-1234567890123456";
    private static final long AMOUNT = 10_000L;
    private static final long INITIAL_BALANCE = 100_000L;

    @BeforeEach
    void cleanUp() {
        chargeRepository.deleteAll();
        userBalanceRepository.deleteAll();
        userBalanceRepository.save(UserBalance.create(USER_ID, Money.of(INITIAL_BALANCE)));
    }

    // --- 정상 흐름 ---

    @Test
    void charge_bankApproved_returnsCompleted() {
        given(bankTransferClient.transfer(any()))
                .willReturn(new BankTransferResult.Succeeded("TRANSFER-001", Map.of()));

        ChargeResponse response = chargeFacadeService.charge(
                "idem-approved", USER_ID, new ChargeRequest(AMOUNT, BANK_ACCOUNT_ID));

        assertThat(response.status()).isEqualTo(ChargeStatus.COMPLETED);
        assertThat(response.transferRef()).isEqualTo("TRANSFER-001");

        assertThat(chargeRepository.findByExternalId("idem-approved"))
                .isPresent()
                .hasValueSatisfying(c -> {
                    assertThat(c.getStatus()).isEqualTo(ChargeStatus.COMPLETED);
                    assertThat(c.getTransferRef()).isEqualTo("TRANSFER-001");
                });

        assertThat(userBalanceRepository.findByUserId(USER_ID))
                .isPresent()
                .hasValueSatisfying(b ->
                        assertThat(b.getBalance()).isEqualTo(Money.of(INITIAL_BALANCE + AMOUNT)));
    }

    @Test
    void charge_bankDeclined_returnsFailed() {
        given(bankTransferClient.transfer(any()))
                .willReturn(new BankTransferResult.Failed("INSUFFICIENT_BALANCE", "계좌 잔액 부족", Map.of()));

        ChargeResponse response = chargeFacadeService.charge(
                "idem-declined", USER_ID, new ChargeRequest(AMOUNT, BANK_ACCOUNT_ID));

        assertThat(response.status()).isEqualTo(ChargeStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("계좌 잔액 부족");

        assertThat(chargeRepository.findByExternalId("idem-declined"))
                .isPresent()
                .hasValueSatisfying(c -> {
                    assertThat(c.getStatus()).isEqualTo(ChargeStatus.FAILED);
                    assertThat(c.getFailureReason()).isEqualTo("계좌 잔액 부족");
                });
    }

    @Test
    void charge_bankException_throwsUpstreamUnavailable_andChargeIsFailed() {
        given(bankTransferClient.transfer(any()))
                .willThrow(new BankTransferException("connection refused"));

        assertThatThrownBy(() -> chargeFacadeService.charge(
                "idem-bank-err", USER_ID, new ChargeRequest(AMOUNT, BANK_ACCOUNT_ID)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorType())
                        .isEqualTo(ChargeErrorType.UPSTREAM_UNAVAILABLE));

        assertThat(chargeRepository.findByExternalId("idem-bank-err"))
                .isPresent()
                .hasValueSatisfying(c -> {
                    assertThat(c.getStatus()).isEqualTo(ChargeStatus.FAILED);
                    assertThat(c.getFailureReason()).isEqualTo("UPSTREAM_FAILURE");
                });

        verify(bankTransferClient, times(1)).transfer(any());
    }

    // --- 멱등성 ---

    @Test
    void charge_sameKeyTwice_replaysPreviousResult_withoutCallingBankAgain() {
        given(bankTransferClient.transfer(any()))
                .willReturn(new BankTransferResult.Succeeded("TRANSFER-002", Map.of()));

        ChargeRequest request = new ChargeRequest(AMOUNT, BANK_ACCOUNT_ID);
        ChargeResponse first  = chargeFacadeService.charge("idem-replay", USER_ID, request);
        ChargeResponse second = chargeFacadeService.charge("idem-replay", USER_ID, request);

        assertThat(second.chargeId()).isEqualTo(first.chargeId());
        assertThat(second.status()).isEqualTo(first.status());
        assertThat(second.transferRef()).isEqualTo(first.transferRef());
        verify(bankTransferClient, times(1)).transfer(any());
    }

    @Test
    void charge_sameKeyDifferentUserId_throwsIdempotencyConflict() {
        given(bankTransferClient.transfer(any()))
                .willReturn(new BankTransferResult.Succeeded("TRANSFER-003", Map.of()));

        chargeFacadeService.charge("idem-conflict", USER_ID, new ChargeRequest(AMOUNT, BANK_ACCOUNT_ID));

        assertThatThrownBy(() ->
                chargeFacadeService.charge("idem-conflict", 99L, new ChargeRequest(AMOUNT, BANK_ACCOUNT_ID)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorType())
                        .isEqualTo(ChargeErrorType.IDEMPOTENCY_CONFLICT));
    }

    // --- 동시성 ---

    @Test
    void charge_concurrentDuplicateInsert_bothThreadsGetValidResult() throws InterruptedException {
        given(bankTransferClient.transfer(any()))
                .willReturn(new BankTransferResult.Succeeded("TRANSFER-CONCURRENT", Map.of()));

        ChargeRequest request = new ChargeRequest(AMOUNT, BANK_ACCOUNT_ID);
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
        assertThat(result1.get()).isNotNull();
        assertThat(result2.get()).isNotNull();
        // 두 스레드가 같은 chargeId를 받는다 (이중 처리 방지)
        assertThat(result1.get().chargeId()).isEqualTo(result2.get().chargeId());
        // 은행 이체는 정확히 1번만 호출된다
        verify(bankTransferClient, times(1)).transfer(any());
        // 경쟁에서 진 스레드는 아직 진행 중인 charge의 PENDING 상태를 반환할 수 있다
        assertThat(result1.get().status()).isIn(ChargeStatus.PENDING, ChargeStatus.COMPLETED, ChargeStatus.FAILED);
        assertThat(result2.get().status()).isIn(ChargeStatus.PENDING, ChargeStatus.COMPLETED, ChargeStatus.FAILED);
    }
}