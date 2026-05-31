package juyeop.jpay.transfer.service;

import juyeop.jpay.common.event.TransferRequestedEvent;
import juyeop.jpay.transfer.AbstractTransferIntegrationTest;
import juyeop.jpay.transfer.entity.Transfer;
import juyeop.jpay.transfer.entity.TransferStatus;
import juyeop.jpay.transfer.external.ExternalBankClient;
import juyeop.jpay.transfer.external.ExternalBankException;
import juyeop.jpay.transfer.external.dto.ExternalBankTransferRequest;
import juyeop.jpay.transfer.external.dto.ExternalBankTransferResult;
import juyeop.jpay.transfer.producer.TransferEventProducer;
import juyeop.jpay.transfer.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
class TransferServiceIntegrationTest extends AbstractTransferIntegrationTest {

    @Autowired
    TransferService transferService;

    @Autowired
    TransferTxService transferTxService;

    @Autowired
    TransferRepository transferRepository;

    @MockitoBean
    ExternalBankClient externalBankClient;

    @MockitoBean
    TransferEventProducer transferEventProducer;

    @MockitoBean
    KafkaTemplate<String, String> kafkaTemplate;

    private static final String EXTERNAL_ID   = "SETTLE-merchant-1-20240101";
    private static final String MERCHANT_ID   = "merchant-1";
    private static final String BANK_ACCOUNT  = "bank-acc-001";
    private static final long   AMOUNT        = 100_000L;

    private TransferRequestedEvent defaultEvent;

    @BeforeEach
    void setUp() {
        transferRepository.deleteAllInBatch();
        defaultEvent = new TransferRequestedEvent(EXTERNAL_ID, MERCHANT_ID, BANK_ACCOUNT, AMOUNT, Instant.now());
    }

    // -------------------------------------------------------------------------
    // 정상 흐름
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("외부은행 성공 → Transfer COMPLETED, publishCompleted 호출")
    void execute_bankSucceeds_transferCompletedAndPublished() {
        given(externalBankClient.transfer(any()))
                .willReturn(new ExternalBankTransferResult.Succeeded("BANK-REF-001", Map.of()));

        transferService.execute(defaultEvent);

        Transfer saved = transferRepository.findByExternalId(EXTERNAL_ID).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(saved.getTransferRef()).isEqualTo("BANK-REF-001");

        ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferEventProducer).publishCompleted(captor.capture());
        assertThat(captor.getValue().getExternalId()).isEqualTo(EXTERNAL_ID);
        verify(transferEventProducer, never()).publishFailed(any());
    }

    @Test
    @DisplayName("외부은행 거절 → Transfer FAILED, publishFailed 호출")
    void execute_bankFails_transferFailedAndPublished() {
        given(externalBankClient.transfer(any()))
                .willReturn(new ExternalBankTransferResult.Failed("INSUFFICIENT_FUNDS", "잔액부족", Map.of()));

        transferService.execute(defaultEvent);

        Transfer saved = transferRepository.findByExternalId(EXTERNAL_ID).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(saved.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");

        verify(transferEventProducer).publishFailed(any());
        verify(transferEventProducer, never()).publishCompleted(any());
    }

    @Test
    @DisplayName("외부은행 예외 → Transfer FAILED, publishFailed 호출")
    void execute_bankThrowsException_transferFailedAndPublished() {
        given(externalBankClient.transfer(any()))
                .willThrow(new ExternalBankException("connection timeout"));

        transferService.execute(defaultEvent);

        Transfer saved = transferRepository.findByExternalId(EXTERNAL_ID).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(saved.getFailureReason()).isEqualTo("UPSTREAM_FAILURE");

        verify(transferEventProducer).publishFailed(any());
        verify(transferEventProducer, never()).publishCompleted(any());
    }

    // -------------------------------------------------------------------------
    // 멱등성 — 재처리(replay)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("이미 COMPLETED인 이벤트 재수신 → 외부은행 재호출 없이 publishCompleted만 재발행")
    void execute_alreadyCompleted_replaysWithoutBankCall() {
        Transfer pending = transferTxService.createPending(EXTERNAL_ID, MERCHANT_ID, BANK_ACCOUNT,
                juyeop.jpay.common.core.Money.of(AMOUNT));
        transferTxService.completeTransfer(pending.getId(), "BANK-REF-PREV");

        transferService.execute(defaultEvent);

        verify(externalBankClient, never()).transfer(any(ExternalBankTransferRequest.class));
        verify(transferEventProducer).publishCompleted(any());
        verify(transferEventProducer, never()).publishFailed(any());
    }

    @Test
    @DisplayName("이미 FAILED인 이벤트 재수신 → 외부은행 재호출 없이 publishFailed만 재발행")
    void execute_alreadyFailed_replaysWithoutBankCall() {
        Transfer pending = transferTxService.createPending(EXTERNAL_ID, MERCHANT_ID, BANK_ACCOUNT,
                juyeop.jpay.common.core.Money.of(AMOUNT));
        transferTxService.failTransfer(pending.getId(), "BANK_DECLINE");

        transferService.execute(defaultEvent);

        verify(externalBankClient, never()).transfer(any(ExternalBankTransferRequest.class));
        verify(transferEventProducer).publishFailed(any());
        verify(transferEventProducer, never()).publishCompleted(any());
    }

    @Test
    @DisplayName("이미 PENDING인 이벤트 재수신 → 외부은행 재호출 없이 아무것도 발행하지 않음")
    void execute_alreadyPending_skipsReplay() {
        transferTxService.createPending(EXTERNAL_ID, MERCHANT_ID, BANK_ACCOUNT,
                juyeop.jpay.common.core.Money.of(AMOUNT));

        transferService.execute(defaultEvent);

        verify(externalBankClient, never()).transfer(any(ExternalBankTransferRequest.class));
        verify(transferEventProducer, never()).publishCompleted(any());
        verify(transferEventProducer, never()).publishFailed(any());
    }

    @Test
    @DisplayName("동일 externalId지만 merchantId가 다른 이벤트 → 충돌 감지, 이벤트 미발행")
    void execute_idempotencyConflict_noEventPublished() {
        transferTxService.createPending(EXTERNAL_ID, "other-merchant", BANK_ACCOUNT,
                juyeop.jpay.common.core.Money.of(AMOUNT));

        TransferRequestedEvent conflictEvent = new TransferRequestedEvent(
                EXTERNAL_ID, MERCHANT_ID, BANK_ACCOUNT, AMOUNT, Instant.now());

        transferService.execute(conflictEvent);

        verify(externalBankClient, never()).transfer(any(ExternalBankTransferRequest.class));
        verify(transferEventProducer, never()).publishCompleted(any());
        verify(transferEventProducer, never()).publishFailed(any());
    }
}