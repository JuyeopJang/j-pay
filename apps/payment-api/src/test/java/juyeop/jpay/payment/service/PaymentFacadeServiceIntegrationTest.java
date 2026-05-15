package juyeop.jpay.payment.service;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.web.error.BusinessException;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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
@Testcontainers
class PaymentFacadeServiceIntegrationTest {

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
		// given
		String externalId = UUID.randomUUID().toString();

		// when
		PaymentResponse response = paymentFacadeService.payOptimistic(externalId, USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID));

		// then
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
	void pay_insufficientBalance_returnsFailed() {
		// given
		String externalId = UUID.randomUUID().toString();

		// when
		PaymentResponse response = paymentFacadeService.payOptimistic(externalId, USER_ID, new PaymentRequest(OVER_LIMIT_AMOUNT, MERCHANT_ID));

		// then
		assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
		assertThat(response.failureReason()).isNotNull();
		assertThat(paymentRepository.findByExternalId(externalId))
				.isPresent()
				.hasValueSatisfying(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED));
		assertThat(userBalanceRepository.findByUserId(USER_ID))
				.isPresent()
				.hasValueSatisfying(balance -> assertThat(balance.getBalance()).isEqualTo(Money.of(INITIAL_BALANCE)));
	}

	@Test
	void pay_balanceNotFound_returnsFailed() {
		// given
		String externalId = UUID.randomUUID().toString();

		// when
		PaymentResponse response = paymentFacadeService.payOptimistic(externalId, UNKNOWN_USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID));

		// then
		assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
		assertThat(paymentRepository.findByExternalId(externalId))
				.isPresent()
				.hasValueSatisfying(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED));
	}

	// =========================================================================
	// 멱등성
	// =========================================================================

	@Test
	void pay_sameKeyTwice_replaysPreviousResult() {
		// given
		String externalId = UUID.randomUUID().toString();

		// when
		PaymentResponse response1 = paymentFacadeService.payOptimistic(externalId, USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID));
		PaymentResponse response2 = paymentFacadeService.payOptimistic(externalId, USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID));

		// then
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
		// given
		String externalId = UUID.randomUUID().toString();
		paymentFacadeService.payOptimistic(externalId, USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID));

		// when & then
		assertThatThrownBy(() -> paymentFacadeService.payOptimistic(externalId, UNKNOWN_USER_ID, new PaymentRequest(AMOUNT, MERCHANT_ID)))
				.isExactlyInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorType())
						.isEqualTo(PaymentErrorType.IDEMPOTENCY_CONFLICT));
	}

	@Test
	void pay_concurrentDuplicateInsert_bothThreadsGetSamePaymentId() throws InterruptedException {
		// given
		PaymentResponse[] responses = new PaymentResponse[2];
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		String externalId = UUID.randomUUID().toString();

		// when
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

		// then
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
		// given
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(100);

		// when
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

		// then
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
		// given
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(100);

		// when
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

		// then
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
		// given
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(100);

		// when
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

		// then
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
