package juyeop.jpay.ledger.service;

import juyeop.jpay.common.event.ChargeCompletedEvent;
import juyeop.jpay.common.event.PaymentCompletedEvent;
import juyeop.jpay.common.event.UserTransferCompletedEvent;
import juyeop.jpay.ledger.AbstractLedgerIntegrationTest;
import juyeop.jpay.ledger.entity.Account;
import juyeop.jpay.ledger.entity.AccountType;
import juyeop.jpay.ledger.entity.LedgerEntry;
import juyeop.jpay.ledger.entity.NormalSide;
import juyeop.jpay.ledger.entity.TransactionStatus;
import juyeop.jpay.ledger.entity.TransactionType;
import juyeop.jpay.ledger.repository.AccountRepository;
import juyeop.jpay.ledger.repository.LedgerEntryRepository;
import juyeop.jpay.ledger.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
class LedgerServiceIntegrationTest extends AbstractLedgerIntegrationTest {

    @Autowired
    LedgerService ledgerService;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    LedgerEntryRepository ledgerEntryRepository;

    @MockitoBean
    KafkaTemplate<String, String> kafkaTemplate;

    private static final long USER_ID = 1L;
    private static final long FROM_USER_ID = 1L;
    private static final long TO_USER_ID = 2L;
    private static final long MERCHANT_ID = 100L;
    private static final long AMOUNT = 10_000L;

    private Long userAccountId;
    private Long toUserAccountId;
    private Long merchantAccountId;
    private Long operatingCashAccountId;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAllInBatch();
        ledgerTransactionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();

        Account userAccount = accountRepository.save(Account.create(AccountType.USER_MONEY, USER_ID));
        Account toUserAccount = accountRepository.save(Account.create(AccountType.USER_MONEY, TO_USER_ID));
        Account merchantAccount = accountRepository.save(Account.create(AccountType.MERCHANT_RECEIVABLE, MERCHANT_ID));
        Account operatingCash = accountRepository.save(Account.create(AccountType.OPERATING_CASH, 0L));

        userAccountId = userAccount.getId();
        toUserAccountId = toUserAccount.getId();
        merchantAccountId = merchantAccount.getId();
        operatingCashAccountId = operatingCash.getId();
    }

    // -------------------------------------------------------------------------
    // recordCharge
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("충전 이벤트 → POSTED 트랜잭션 + CREDIT(사용자)/DEBIT(운영계좌) 분개 생성")
    void recordCharge_createsPostedTransactionWithDoubleEntry() {
        ChargeCompletedEvent event = new ChargeCompletedEvent(1001L, USER_ID, AMOUNT, Instant.now());

        ledgerService.record(event);

        var transactions = ledgerTransactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        var tx = transactions.get(0);
        assertThat(tx.getTransactionType()).isEqualTo(TransactionType.CHARGE);
        assertThat(tx.getTransactionStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(tx.getTotalAmount().amount()).isEqualTo(AMOUNT);
        assertThat(tx.getExternalId()).isEqualTo("1001");

        List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        assertThat(entries).hasSize(2);

        LedgerEntry creditEntry = findEntry(entries, userAccountId);
        assertThat(creditEntry.getSide()).isEqualTo(NormalSide.CREDIT);
        assertThat(creditEntry.getAmount().amount()).isEqualTo(AMOUNT);

        LedgerEntry debitEntry = findEntry(entries, operatingCashAccountId);
        assertThat(debitEntry.getSide()).isEqualTo(NormalSide.DEBIT);
        assertThat(debitEntry.getAmount().amount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("충전 이벤트 중복 수신 → 두 번째 호출은 무시 (멱등성)")
    void recordCharge_duplicateEventId_isIgnored() {
        ChargeCompletedEvent event = new ChargeCompletedEvent(2001L, USER_ID, AMOUNT, Instant.now());

        ledgerService.record(event);
        ledgerService.record(event);

        assertThat(ledgerTransactionRepository.findAll()).hasSize(1);
        assertThat(ledgerEntryRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("충전 이벤트 — 계좌 없으면 IllegalStateException")
    void recordCharge_unknownAccount_throwsIllegalStateException() {
        ChargeCompletedEvent event = new ChargeCompletedEvent(3001L, 999L, AMOUNT, Instant.now());

        assertThatThrownBy(() -> ledgerService.record(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Account not found");
    }

    // -------------------------------------------------------------------------
    // recordPayment
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("결제 이벤트 → POSTED 트랜잭션 + DEBIT(사용자)/CREDIT(가맹점) 분개 생성")
    void recordPayment_createsPostedTransactionWithDoubleEntry() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                4001L, USER_ID, String.valueOf(MERCHANT_ID), AMOUNT, Instant.now());

        ledgerService.record(event);

        var transactions = ledgerTransactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        var tx = transactions.get(0);
        assertThat(tx.getTransactionType()).isEqualTo(TransactionType.PAYMENT);
        assertThat(tx.getTransactionStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(tx.getTotalAmount().amount()).isEqualTo(AMOUNT);

        List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        assertThat(entries).hasSize(2);

        LedgerEntry debitEntry = findEntry(entries, userAccountId);
        assertThat(debitEntry.getSide()).isEqualTo(NormalSide.DEBIT);

        LedgerEntry creditEntry = findEntry(entries, merchantAccountId);
        assertThat(creditEntry.getSide()).isEqualTo(NormalSide.CREDIT);
    }

    @Test
    @DisplayName("결제 이벤트 중복 수신 → 두 번째 호출은 무시 (멱등성)")
    void recordPayment_duplicateEventId_isIgnored() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                5001L, USER_ID, String.valueOf(MERCHANT_ID), AMOUNT, Instant.now());

        ledgerService.record(event);
        ledgerService.record(event);

        assertThat(ledgerTransactionRepository.findAll()).hasSize(1);
        assertThat(ledgerEntryRepository.findAll()).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // recordTransfer
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("송금 이벤트 → POSTED 트랜잭션 + DEBIT(송신자)/CREDIT(수신자) 분개 생성")
    void recordTransfer_createsPostedTransactionWithDoubleEntry() {
        UserTransferCompletedEvent event = new UserTransferCompletedEvent(
                6001L, FROM_USER_ID, TO_USER_ID, AMOUNT, Instant.now());

        ledgerService.record(event);

        var transactions = ledgerTransactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        var tx = transactions.get(0);
        assertThat(tx.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(tx.getTransactionStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(tx.getTotalAmount().amount()).isEqualTo(AMOUNT);

        List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        assertThat(entries).hasSize(2);

        LedgerEntry senderEntry = findEntry(entries, userAccountId);
        assertThat(senderEntry.getSide()).isEqualTo(NormalSide.DEBIT);

        LedgerEntry receiverEntry = findEntry(entries, toUserAccountId);
        assertThat(receiverEntry.getSide()).isEqualTo(NormalSide.CREDIT);
    }

    @Test
    @DisplayName("송금 이벤트 중복 수신 → 두 번째 호출은 무시 (멱등성)")
    void recordTransfer_duplicateEventId_isIgnored() {
        UserTransferCompletedEvent event = new UserTransferCompletedEvent(
                7001L, FROM_USER_ID, TO_USER_ID, AMOUNT, Instant.now());

        ledgerService.record(event);
        ledgerService.record(event);

        assertThat(ledgerTransactionRepository.findAll()).hasSize(1);
        assertThat(ledgerEntryRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("동시 중복 충전 이벤트 → 한 스레드만 성공, 나머지는 DataIntegrityViolationException, DB는 1건")
    void recordCharge_concurrentDuplicate_onlyOneSucceeds() throws InterruptedException {
        ChargeCompletedEvent event = new ChargeCompletedEvent(8001L, USER_ID, AMOUNT, Instant.now());

        int threadCount = 4;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ledgerService.record(event);
                    successCount.incrementAndGet();
                } catch (DataIntegrityViolationException e) {
                    // consumer가 잡아야 할 예외 — 정상 경로
                    duplicateCount.incrementAndGet();
                } catch (Exception e) {
                    // 그 외 예외는 테스트 실패로 이어지도록 무시하지 않음
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(threadCount - 1);
        assertThat(ledgerTransactionRepository.findAll()).hasSize(1);
        assertThat(ledgerEntryRepository.findAll()).hasSize(2);
    }

    private LedgerEntry findEntry(List<LedgerEntry> entries, Long accountId) {
        return entries.stream()
                .filter(e -> e.getAccountId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Entry not found for accountId: " + accountId));
    }
}
