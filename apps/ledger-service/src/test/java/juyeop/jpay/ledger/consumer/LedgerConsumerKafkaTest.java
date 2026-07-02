package juyeop.jpay.ledger.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.event.ChargeCompletedEvent;
import juyeop.jpay.common.event.PaymentCompletedEvent;
import juyeop.jpay.ledger.AbstractLedgerKafkaIntegrationTest;
import juyeop.jpay.ledger.entity.Account;
import juyeop.jpay.ledger.entity.AccountType;
import juyeop.jpay.ledger.entity.NormalSide;
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

import java.time.Duration;
import java.time.Instant;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.snowflake.node-id=99"
        }
)
class LedgerConsumerKafkaTest extends AbstractLedgerKafkaIntegrationTest {

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired LedgerTransactionRepository ledgerTransactionRepository;
    @Autowired LedgerEntryRepository ledgerEntryRepository;

    private static final long USER_ID     = 1L;
    private static final long MERCHANT_ID = 100L;
    private static final long AMOUNT      = 10_000L;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAllInBatch();
        ledgerTransactionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();

        accountRepository.save(Account.create(AccountType.USER_MONEY, USER_ID));
        accountRepository.save(Account.create(AccountType.MERCHANT_RECEIVABLE, MERCHANT_ID));
        accountRepository.save(Account.create(AccountType.OPERATING_CASH, 0L));
    }

    @Test
    @DisplayName("charge.completed 수신 → LedgerTransaction + CREDIT(사용자)/DEBIT(운영계좌) 분개 생성")
    void chargeCompleted_createsDoubleEntryLedger() throws Exception {
        ChargeCompletedEvent event = new ChargeCompletedEvent(1001L, USER_ID, AMOUNT, Instant.now());
        kafkaTemplate.send(ChargeCompletedEvent.TOPIC, objectMapper.writeValueAsString(event));

        await().atMost(10, SECONDS)
               .pollInterval(Duration.ofMillis(300))
               .untilAsserted(() -> {
                   assertThat(ledgerTransactionRepository.findAll()).hasSize(1);
                   var tx = ledgerTransactionRepository.findAll().get(0);
                   assertThat(tx.getTransactionType()).isEqualTo(TransactionType.CHARGE);
                   assertThat(tx.getTotalAmount().amount()).isEqualTo(AMOUNT);

                   var entries = ledgerEntryRepository.findAll();
                   assertThat(entries).hasSize(2);
                   assertThat(entries).anySatisfy(e -> assertThat(e.getSide()).isEqualTo(NormalSide.CREDIT));
                   assertThat(entries).anySatisfy(e -> assertThat(e.getSide()).isEqualTo(NormalSide.DEBIT));
               });
    }

    @Test
    @DisplayName("payment.completed 수신 → DEBIT(사용자)/CREDIT(가맹점) 분개 생성")
    void paymentCompleted_createsDoubleEntryLedger() throws Exception {
        PaymentCompletedEvent event = new PaymentCompletedEvent(2001L, USER_ID, String.valueOf(MERCHANT_ID), AMOUNT, Instant.now());
        kafkaTemplate.send(PaymentCompletedEvent.TOPIC, objectMapper.writeValueAsString(event));

        await().atMost(10, SECONDS)
               .pollInterval(Duration.ofMillis(300))
               .untilAsserted(() -> {
                   assertThat(ledgerTransactionRepository.findAll()).hasSize(1);
                   var tx = ledgerTransactionRepository.findAll().get(0);
                   assertThat(tx.getTransactionType()).isEqualTo(TransactionType.PAYMENT);

                   var entries = ledgerEntryRepository.findAll();
                   assertThat(entries).hasSize(2);
               });
    }

    @Test
    @DisplayName("동일 이벤트 중복 수신 — 두 번째 메시지는 무시되어 LedgerTransaction 1건 유지")
    void duplicateEvent_isIdempotent() throws Exception {
        ChargeCompletedEvent event = new ChargeCompletedEvent(3001L, USER_ID, AMOUNT, Instant.now());
        String payload = objectMapper.writeValueAsString(event);

        kafkaTemplate.send(ChargeCompletedEvent.TOPIC, payload);
        kafkaTemplate.send(ChargeCompletedEvent.TOPIC, payload);

        await().atMost(10, SECONDS)
               .pollInterval(Duration.ofMillis(300))
               .untilAsserted(() ->
                       assertThat(ledgerTransactionRepository.findAll()).hasSize(1));
    }
}