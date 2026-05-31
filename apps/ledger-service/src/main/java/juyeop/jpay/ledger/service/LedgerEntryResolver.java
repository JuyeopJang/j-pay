package juyeop.jpay.ledger.service;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.event.ChargeCompletedEvent;
import juyeop.jpay.common.event.LedgerEvent;
import juyeop.jpay.common.event.PaymentCompletedEvent;
import juyeop.jpay.common.event.UserTransferCompletedEvent;
import juyeop.jpay.ledger.entity.*;
import juyeop.jpay.ledger.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class LedgerEntryResolver {

    private final AccountRepository accountRepository;

    List<LedgerEntry> resolve(LedgerEvent event, Long txId, Money amount) {
        return switch (event) {
            case ChargeCompletedEvent e -> chargeEntries(txId, e, amount);
            case PaymentCompletedEvent e -> paymentEntries(txId, e, amount);
            case UserTransferCompletedEvent e -> transferEntries(txId, e, amount);
            default -> throw new IllegalArgumentException("Unsupported ledger event type: " + event.getClass());
        };
    }

    TransactionType resolveTransactionType(LedgerEvent event) {
        return switch (event) {
            case ChargeCompletedEvent ignored -> TransactionType.CHARGE;
            case PaymentCompletedEvent ignored -> TransactionType.PAYMENT;
            case UserTransferCompletedEvent ignored -> TransactionType.TRANSFER;
            default -> throw new IllegalArgumentException("Unsupported ledger event type: " + event.getClass());
        };
    }

    private List<LedgerEntry> chargeEntries(Long txId, ChargeCompletedEvent e, Money amount) {
        Account user = findAccount(AccountType.USER_MONEY, e.userId());
        Account owner = findAccount(AccountType.OPERATING_CASH, 0L);
        return List.of(
                LedgerEntry.create(txId, user.getId(), NormalSide.CREDIT, amount),
                LedgerEntry.create(txId, owner.getId(), NormalSide.DEBIT, amount)
        );
    }

    private List<LedgerEntry> paymentEntries(Long txId, PaymentCompletedEvent e, Money amount) {
        Account user = findAccount(AccountType.USER_MONEY, e.userId());
        Account merchant = findAccount(AccountType.MERCHANT_RECEIVABLE, Long.parseLong(e.merchantId()));
        return List.of(
                LedgerEntry.create(txId, user.getId(), NormalSide.DEBIT, amount),
                LedgerEntry.create(txId, merchant.getId(), NormalSide.CREDIT, amount)
        );
    }

    private List<LedgerEntry> transferEntries(Long txId, UserTransferCompletedEvent e, Money amount) {
        Account sender = findAccount(AccountType.USER_MONEY, e.fromUserId());
        Account receiver = findAccount(AccountType.USER_MONEY, e.toUserId());
        return List.of(
                LedgerEntry.create(txId, sender.getId(), NormalSide.DEBIT, amount),
                LedgerEntry.create(txId, receiver.getId(), NormalSide.CREDIT, amount)
        );
    }

    private Account findAccount(AccountType accountType, Long ownerId) {
        return accountRepository.findByAccountTypeAndOwnerId(accountType, ownerId)
                .orElseThrow(() -> new IllegalStateException(
                        "Account not found: type=%s, ownerId=%d".formatted(accountType, ownerId)));
    }
}