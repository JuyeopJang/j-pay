package juyeop.jpay.ledger.service;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.event.LedgerEvent;
import juyeop.jpay.ledger.entity.LedgerTransaction;
import juyeop.jpay.ledger.repository.LedgerEntryRepository;
import juyeop.jpay.ledger.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerEntryResolver entryResolver;

    @Transactional
    public void record(LedgerEvent event) {
        String externalId = String.valueOf(event.entityId());
        if (ledgerTransactionRepository.existsByExternalId(externalId)) {
            log.info("Event already processed: {}", externalId);
            return;
        }

        Money amount = Money.of(event.amount());
        LedgerTransaction tx = ledgerTransactionRepository.saveAndFlush(
                LedgerTransaction.create(externalId,
                        entryResolver.resolveTransactionType(event),
                        amount,
                        event.occurredAt()));

        ledgerEntryRepository.saveAll(entryResolver.resolve(event, tx.getId(), amount));
    }
}