package juyeop.jpay.batch.reconciliation;

import juyeop.jpay.batch.reconciliation.dto.BankTransaction;

import java.time.LocalDate;
import java.util.List;

public interface BankMockClient {

    List<BankTransaction> fetchTransactions(LocalDate date);
}
