package juyeop.jpay.payment.service;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.payment.AbstractPaymentIntegrationTest;
import juyeop.jpay.payment.dto.TransferRequest;
import juyeop.jpay.payment.entity.UserBalance;
import juyeop.jpay.payment.repository.UserBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// =============================================================================
// 구현 가이드 — 데드락 방지 검증 통합 테스트
// =============================================================================
//
// [테스트 목적]
//   A→B, B→A 교차 송금을 동시에 실행했을 때 데드락 없이 둘 다 완료되어야 한다.
//
// [시나리오]
//   USER_A (id=1), USER_B (id=2) — 각각 초기 잔액 100,000원
//   스레드 1: A → B 10,000원 송금
//   스레드 2: B → A 10,000원 송금
//   CountDownLatch로 두 스레드가 동시에 출발하게 조정
//
// [검증]
//   - 두 Future 모두 예외 없이 완료
//   - A 최종 잔액 = 100,000 (A가 보내고 받으면 동일)
//   - B 최종 잔액 = 100,000 (동일)
//   - 총 잔액 보존: A.balance + B.balance == 200,000
//
// [힌트]
//   CountDownLatch latch = new CountDownLatch(1);
//   ExecutorService es = Executors.newFixedThreadPool(2);
//   Future<?> f1 = es.submit(() -> { latch.await(); transferService.transfer(A, req_A→B); });
//   Future<?> f2 = es.submit(() -> { latch.await(); transferService.transfer(B, req_B→A); });
//   latch.countDown(); // 동시 출발
//   f1.get(5, TimeUnit.SECONDS);
//   f2.get(5, TimeUnit.SECONDS);
//
// [주의]
//   테스트가 TimeoutException으로 실패하면 데드락 발생. 락 순서를 다시 확인할 것.
// =============================================================================

@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                "app.snowflake.node-id=99"
        }
)
class TransferServiceDeadlockTest extends AbstractPaymentIntegrationTest {

    @Autowired
    TransferService transferService;

    @Autowired
    UserBalanceRepository userBalanceRepository;

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final long INITIAL_BALANCE = 1_000_000L;
    private static final long TRANSFER_AMOUNT = 10_000L;

    @BeforeEach
    void setUp() {
        userBalanceRepository.deleteAll();
        userBalanceRepository.save(UserBalance.create(USER_A, Money.of(INITIAL_BALANCE)));
        userBalanceRepository.save(UserBalance.create(USER_B, Money.of(INITIAL_BALANCE)));
    }

    @Test
    @DisplayName("A→B, B→A 교차 송금 동시 실행 시 데드락 없이 완료")
    void crossTransfer_noDeadlock() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount / 2; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    transferService.transfer(USER_A, new TransferRequest(USER_B, TRANSFER_AMOUNT));
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    transferService.transfer(USER_B, new TransferRequest(USER_A, TRANSFER_AMOUNT));
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed).as("데드락 발생 — 10초 내 완료되지 않음").isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);

        long balanceA = userBalanceRepository.findByUserId(USER_A).orElseThrow().getBalance().amount();
        long balanceB = userBalanceRepository.findByUserId(USER_B).orElseThrow().getBalance().amount();
        assertThat(balanceA).isEqualTo(INITIAL_BALANCE);
        assertThat(balanceB).isEqualTo(INITIAL_BALANCE);
    }
}