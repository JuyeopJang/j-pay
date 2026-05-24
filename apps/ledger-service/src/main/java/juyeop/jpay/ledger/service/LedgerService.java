package juyeop.jpay.ledger.service;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.event.ChargeCompletedEvent;
import juyeop.jpay.common.event.PaymentCompletedEvent;
import juyeop.jpay.ledger.entity.*;
import juyeop.jpay.ledger.repository.AccountRepository;
import juyeop.jpay.ledger.repository.LedgerEntryRepository;
import juyeop.jpay.ledger.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public void recordPayment(PaymentCompletedEvent event) {
        if (ledgerTransactionRepository.existsByExternalId(String.valueOf(event.paymentId()))) {
            log.info("Payment already processed: {}", event);
            return;
        }

        Money amount = Money.of(event.amount());
        Account userAccount = findAccount(AccountType.USER_MONEY, event.userId());
        Account merchantAccount = findAccount(AccountType.MERCHANT_RECEIVABLE, Long.parseLong(event.merchantId()));

        LedgerTransaction tx = ledgerTransactionRepository.saveAndFlush(
                LedgerTransaction.create(String.valueOf(event.paymentId()), TransactionType.PAYMENT, amount, event.occurredAt()));

        ledgerEntryRepository.saveAll(List.of(
                LedgerEntry.create(tx.getId(), userAccount.getId(), NormalSide.DEBIT, amount),
                LedgerEntry.create(tx.getId(), merchantAccount.getId(), NormalSide.CREDIT, amount)
        ));
    }

    @Transactional
    public void recordCharge(ChargeCompletedEvent event) {
        if (ledgerTransactionRepository.existsByExternalId(String.valueOf(event.chargeId()))) {
            log.info("Charge already processed: {}", event);
            return;
        }

        Money amount = Money.of(event.amount());
        Account userAccount = findAccount(AccountType.USER_MONEY, event.userId());
        Account ownerAccount = findAccount(AccountType.OPERATING_CASH, 0L);

        LedgerTransaction tx = ledgerTransactionRepository.saveAndFlush(
                LedgerTransaction.create(String.valueOf(event.chargeId()), TransactionType.CHARGE, amount, event.occurredAt()));

        ledgerEntryRepository.saveAll(List.of(
                LedgerEntry.create(tx.getId(), userAccount.getId(), NormalSide.CREDIT, amount),
                LedgerEntry.create(tx.getId(), ownerAccount.getId(), NormalSide.DEBIT, amount)
        ));
    }

    private Account findAccount(AccountType accountType, Long ownerId) {
        return accountRepository.findByAccountTypeAndOwnerId(accountType, ownerId)
                .orElseThrow(() -> new IllegalStateException(
                        "Account not found: type=%s, ownerId=%d".formatted(accountType, ownerId)));
    }
}