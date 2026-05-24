package juyeop.jpay.batch.reconciliation;

import juyeop.jpay.batch.reconciliation.dto.BankTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BankMockClientImpl implements BankMockClient {

    private final RestClient bankMockRestClient;

    @Override
    public List<BankTransaction> fetchTransactions(LocalDate date) {
        return bankMockRestClient.get()
                .uri("/internal/bank-mock/transactions?date={date}", date)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
