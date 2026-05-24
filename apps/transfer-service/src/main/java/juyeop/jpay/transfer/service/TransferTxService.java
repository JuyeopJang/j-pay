package juyeop.jpay.transfer.service;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.transfer.entity.Transfer;
import juyeop.jpay.transfer.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransferTxService {

    private final TransferRepository transferRepository;

    @Transactional(readOnly = true)
    public Optional<Transfer> findByExternalId(String externalId) {
        return transferRepository.findByExternalId(externalId);
    }

    @Transactional
    public Transfer createPending(String externalId, String merchantId, String bankAccountId, Money amount) {
        return transferRepository.save(Transfer.pending(externalId, merchantId, bankAccountId, amount));
    }

    @Transactional
    public Transfer completeTransfer(Long transferId, String transferRef) {
        Transfer transfer = getById(transferId);
        transfer.complete(transferRef);
        return transfer;
    }

    @Transactional
    public Transfer failTransfer(Long transferId, String reason) {
        Transfer transfer = getById(transferId);
        transfer.fail(reason);
        return transfer;
    }

    private Transfer getById(Long transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalStateException("Transfer not found: " + transferId));
    }
}
