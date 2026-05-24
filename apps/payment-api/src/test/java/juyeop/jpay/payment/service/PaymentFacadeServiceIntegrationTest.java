package juyeop.jpay.payment.service;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.web.error.BusinessException;
import juyeop.jpay.payment.AbstractPaymentIntegrationTest;
import juyeop.jpay.payment.dto.PaymentRequest;
import juyeop.jpay.payment.dto.PaymentResponse;
import juyeop.jpay.payment.entity.PaymentStatus;
import juyeop.jpay.payment.entity.UserBalance;
import juyeop.jpay.payment.exception.PaymentErrorType;
import juyeop.jpay.payment.repository.PaymentRepository;
import juyeop.jpay.payment.repository.UserBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"spring.jpa.hibernate.ddl-auto=create-drop",
				"spring.autoconfigure.exclude=" +
						"org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
				"app.snowflake.node-id=99"
		}
)
class PaymentFacadeServiceIntegrationTest extends AbstractPaymentIntegrationTest {

	@MockitoBean
	KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	PaymentFacadeService paymentFacadeService;
	@Autowired
	PaymentRepository paymentRepository;
	@Autowired
	UserBalanceRepository userBalanceRepository;

	private static final Long USER_ID = 1L;
	private static final Long UNKNOWN_USER_ID = 2L;
	private static final String MERCHANT_ID = "merchant-abc";
	private static final long INITIAL_BALANCE = 50_000L;
	private static final long AMOUNT = 10_000L;
	private static final long OVER_LIMIT_AMOUNT = 100_000L;

	@BeforeEach
	void setUp() {
		paymentRepository.deleteAll();
		userBalanceRepository.deleteAll();
		userBalanceRepository.save(UserBalance.create(USER_ID, Money.of(INITIAL_BALANCE)));
	}

	// =========================================================================
	// 정상 흐름
	// =========================================================================

	@Test
	void pay_sufficientBalance_returnsCompleted() {
		String externalId = UUID.randomUUID().toString();

		PaymentResponse response = paymentFacadeService.payOptimistic(externalId, USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID));

		assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
		assertThat(response.completedAt()).isNotNull();
		assertThat(paymentRepository.findByExternalId(externalId))
				.isPresent()
				.hasValueSatisfying(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED));
		assertThat(userBalanceRepository.findByUserId(USER_ID))
				.isPresent()
				.hasValueSatisfying(balance -> assertThat(balance.getBalance()).isEqualTo(Money.of(INITIAL_BALANCE - AMOUNT)));
	}

	@Test
	void pay_insufficientBalance_throwsBusinessException() {
		String externalId = UUID.randomUUID().toString();

		assertThatThrownBy(() -> paymentFacadeService.payOptimistic(externalId, USER_ID, new PaymentRequest(OVER_LIMIT_AMOUNT, MERCHANT_ID)))
				.isExactlyInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorType())
						.isEqualTo(PaymentErrorType.INSUFFICIENT_BALANCE));
		assertThat(paymentRepository.findByExternalId(externalId)).isEmpty();
		assertThat(userBalanceRepository.findByUserId(USER_ID))
				.isPresent()
				.hasValueSatisfying(balance -> assertThat(balance.getBalance()).isEqualTo(Money.of(INITIAL_BALANCE)));
	}

	@Test
	void pay_balanceNotFound_throwsBusinessException() {
		String externalId = UUID.randomUUID().toString();

		assertThatThrownBy(() -> paymentFacadeService.payOptimistic(externalId, UNKNOWN_USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID)))
				.isExactlyInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorType())
						.isEqualTo(PaymentErrorType.BALANCE_NOT_FOUND));
		assertThat(paymentRepository.findByExternalId(externalId)).isEmpty();
	}

	// =========================================================================
	// 멱등성
	// =========================================================================

	@Test
	void pay_sameKeyTwice_replaysPreviousResult() {
		String externalId = UUID.randomUUID().toString();

		PaymentResponse response1 = paymentFacadeService.payOptimistic(externalId, USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID));
		PaymentResponse response2 = paymentFacadeService.payOptimistic(externalId, USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID));

		assertThat(response1.status()).isEqualTo(PaymentStatus.COMPLETED);
		assertThat(response2.status()).isEqualTo(PaymentStatus.COMPLETED);
		assertThat(response1.paymentId()).isEqualTo(response2.paymentId());
		assertThat(paymentRepository.findByExternalId(externalId))
				.isPresent()
				.hasValueSatisfying(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED));
		assertThat(userBalanceRepository.findByUserId(USER_ID))
				.isPresent()
				.hasValueSatisfying(balance -> assertThat(balance.getBalance()).isEqualTo(Money.of(INITIAL_BALANCE - AMOUNT)));
		assertThat(paymentRepository.findAll())
				.filteredOn(p -> p.getExternalId().equals(externalId))
				.hasSize(1);
	}

	@Test
	void pay_sameKeyDifferentUserId_throwsIdempotencyConflict() {
		String externalId = UUID.randomUUID().toString();
		paymentFacadeService.payOptimistic(externalId, USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID));

		assertThatThrownBy(() -> paymentFacadeService.payOptimistic(externalId, UNKNOWN_USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID)))
				.isExactlyInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorType())
						.isEqualTo(PaymentErrorType.IDEMPOTENCY_CONFLICT));
	}

	@Test
	void pay_concurrentDuplicateInsert_bothThreadsGetSamePaymentId() throws InterruptedException {
		PaymentResponse[] responses = new PaymentResponse[2];
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		String externalId = UUID.randomUUID().toString();

		for (int i = 0; i < 2; i++) {
			int finalI = i;
			pool.submit(() -> {
				start.await();
				responses[finalI] = paymentFacadeService.payOptimistic(externalId, USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID));
				return null;
			});
		}
		start.countDown();
		pool.shutdown();
		pool.awaitTermination(10, SECONDS);

		assertThat(responses[0].paymentId()).isEqualTo(responses[1].paymentId());
		assertThat(paymentRepository.findAll())
				.filteredOn(p -> p.getExternalId().equals(externalId))
				.hasSize(1);
		assertThat(userBalanceRepository.findByUserId(USER_ID))
				.isPresent()
				.hasValueSatisfying(balance -> assertThat(balance.getBalance()).isEqualTo(Money.of(INITIAL_BALANCE - AMOUNT)));
	}

	// =========================================================================
	// 동시성 잔액 차감 — Optimistic Lock
	// =========================================================================

	@Test
	void payOptimistic_concurrentDeduction_balanceIsConsistent() throws InterruptedException {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(100);

		for (int i = 0; i < 100; i++) {
			final int idx = i;
			pool.submit(() -> {
				start.await();
				paymentFacadeService.payOptimistic("key-" + idx, USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID));
				return null;
			});
		}
		start.countDown();
		pool.shutdown();
		boolean terminated = pool.awaitTermination(10, SECONDS);

		assertThat(terminated).as("threads did not finish in time").isTrue();
		long completed = paymentRepository.findAll().stream()
				.filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
				.count();
		assertThat(userBalanceRepository.findByUserId(USER_ID))
				.isPresent()
				.hasValueSatisfying(balance -> {
					assertThat(balance.getBalance()).isEqualTo(Money.of(INITIAL_BALANCE - completed * AMOUNT));
					assertThat(balance.getBalance().amount()).isGreaterThanOrEqualTo(0);
				});
	}

	@Test
	void payPessimistic_concurrentDeduction_balanceIsConsistent() throws InterruptedException {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(100);

		for (int i = 0; i < 100; i++) {
			final int idx = i;
			pool.submit(() -> {
				start.await();
				paymentFacadeService.payPessimistic("key-" + idx, USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID));
				return null;
			});
		}
		start.countDown();
		pool.shutdown();
		boolean terminated = pool.awaitTermination(10, SECONDS);

		assertThat(terminated).as("threads did not finish in time").isTrue();
		long completed = paymentRepository.findAll().stream()
				.filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
				.count();
		assertThat(completed).as("pessimistic lock must guarantee exactly 5 completions").isEqualTo(5);
		assertThat(userBalanceRepository.findByUserId(USER_ID))
				.isPresent()
				.hasValueSatisfying(balance -> {
					assertThat(balance.getBalance()).isEqualTo(Money.of(INITIAL_BALANCE - completed * AMOUNT));
					assertThat(balance.getBalance().amount()).isGreaterThanOrEqualTo(0);
				});
	}

	@Test
	void payWithRedisLock_concurrentDeduction_balanceIsConsistent() throws InterruptedException {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(100);

		for (int i = 0; i < 100; i++) {
			final int idx = i;
			pool.submit(() -> {
				start.await();
				paymentFacadeService.payWithRedisLock("key-" + idx, USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID));
				return null;
			});
		}
		start.countDown();
		pool.shutdown();
		boolean terminated = pool.awaitTermination(10, SECONDS);

		assertThat(terminated).as("threads did not finish in time").isTrue();
		long completed = paymentRepository.findAll().stream()
				.filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
				.count();
		assertThat(completed).as("pessimistic lock must guarantee exactly 5 completions").isEqualTo(5);
		assertThat(userBalanceRepository.findByUserId(USER_ID))
				.isPresent()
				.hasValueSatisfying(balance -> {
					assertThat(balance.getBalance()).isEqualTo(Money.of(INITIAL_BALANCE - completed * AMOUNT));
					assertThat(balance.getBalance().amount()).isGreaterThanOrEqualTo(0);
				});
	}
}