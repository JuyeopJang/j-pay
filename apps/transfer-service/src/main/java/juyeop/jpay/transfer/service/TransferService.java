package juyeop.jpay.transfer.service;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.event.TransferRequestedEvent;
import juyeop.jpay.transfer.entity.Transfer;
import juyeop.jpay.transfer.external.ExternalBankClient;
import juyeop.jpay.transfer.external.ExternalBankException;
import juyeop.jpay.transfer.external.dto.ExternalBankTransferRequest;
import juyeop.jpay.transfer.external.dto.ExternalBankTransferResult;
import juyeop.jpay.transfer.producer.TransferEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferTxService transferTxService;
    private final ExternalBankClient externalBankClient;
    private final TransferEventProducer transferEventProducer;

    public void execute(TransferRequestedEvent event) {
        transferTxService.findByExternalId(event.externalId())
                .ifPresentOrElse(
                        existing -> replay(existing, event),
                        () -> process(event));
    }

    private void replay(Transfer existing, TransferRequestedEvent event) {
        if (!existing.matches(event.merchantId(), Money.of(event.amount()), event.bankAccountId())) {
            log.warn("Idempotency conflict on transfer: externalId={}", event.externalId());
            return;
        }
        switch (existing.getStatus()) {
            case COMPLETED -> transferEventProducer.publishCompleted(existing);
            case FAILED    -> transferEventProducer.publishFailed(existing);
            case PENDING   -> log.info("Transfer still PENDING, skip replay: externalId={}", event.externalId());
        }
    }

    private void process(TransferRequestedEvent event) {
        Transfer pending;
        try {
            pending = transferTxService.createPending(
                    event.externalId(), event.merchantId(), event.bankAccountId(), Money.of(event.amount()));
        } catch (DataIntegrityViolationException e) {
            Transfer existing = transferTxService.findByExternalId(event.externalId())
                    .orElseThrow(() -> new IllegalStateException("UNIQUE conflict but row not found"));
            replay(existing, event);
            return;
        }

        try {
            ExternalBankTransferResult result = externalBankClient.transfer(
                    new ExternalBankTransferRequest(pending.getId(), event.bankAccountId(), event.amount()));
            applyResult(pending, result);
        } catch (ExternalBankException e) {
            Transfer failed = transferTxService.failTransfer(pending.getId(), "UPSTREAM_FAILURE");
            transferEventProducer.publishFailed(failed);
        }
    }

    private void applyResult(Transfer pending, ExternalBankTransferResult result) {
        switch (result) {
            case ExternalBankTransferResult.Succeeded s -> {
                Transfer completed = transferTxService.completeTransfer(pending.getId(), s.transferRef());
                transferEventProducer.publishCompleted(completed);
            }
            case ExternalBankTransferResult.Failed f -> {
                Transfer failed = transferTxService.failTransfer(pending.getId(), f.code());
                transferEventProducer.publishFailed(failed);
            }
        }
    }
}
